package de.mcbesser.displaygui.sidebar;

import de.mcbesser.displaygui.DisplayGUIPlugin;
import de.mcbesser.displaygui.display.DisplayEntityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class DisplaySidebar {
    private static final String OBJECTIVE_NAME = "displaygui_info";
    private static final int MAX_LINES = 8;

    private final DisplayGUIPlugin plugin;
    private BukkitTask task;

    public DisplaySidebar(DisplayGUIPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }

    public void refresh(Player player) {
        DisplayEntityManager.HoveredDisplayInfo hovered = plugin.getDisplayEntityManager().getHoveredDisplayInfo(player);
        if (hovered == null) {
            clear(player);
            return;
        }

        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, Criteria.DUMMY, Component.text("DisplayGUI", NamedTextColor.GOLD));
        }
        objective.displayName(hovered.title().colorIfAbsent(NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.addAll(hovered.lore());
        while (lines.size() < MAX_LINES) {
            lines.add(Component.empty());
        }

        for (int i = 0; i < MAX_LINES; i++) {
            String entry = org.bukkit.ChatColor.values()[i].toString();
            Team team = scoreboard.getTeam("display_line_" + i);
            if (team == null) {
                team = scoreboard.registerNewTeam("display_line_" + i);
                team.addEntry(entry);
            }
            team.prefix(formatLine(lines.get(i)));
            objective.getScore(entry).setScore(MAX_LINES - i);
        }

        player.setScoreboard(scoreboard);
    }

    public void clear(Player player) {
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        for (int i = 0; i < MAX_LINES; i++) {
            Team team = scoreboard.getTeam("display_line_" + i);
            if (team != null) {
                team.unregister();
            }
        }
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private Component formatLine(Component line) {
        if (line == null) {
            return Component.empty();
        }
        if (!(line instanceof TextComponent textComponent)) {
            return line;
        }
        String text = textComponent.content();
        if (text.isBlank() && textComponent.children().isEmpty()) {
            return Component.empty();
        }
        int separator = text.indexOf(':');
        if (separator < 0) {
            return line;
        }
        String key = text.substring(0, separator).trim();
        String value = text.substring(separator + 1).trim();
        TextComponent.Builder builder = Component.text();
        builder.append(Component.text(key + ":", NamedTextColor.YELLOW));
        if (!value.isEmpty()) {
            builder.append(Component.text(" " + value, NamedTextColor.WHITE));
        }
        return builder.build();
    }
}
