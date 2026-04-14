package de.mcbesser.displaygui.command;

import de.mcbesser.displaygui.DisplayGUIPlugin;
import de.mcbesser.displaygui.feature.crafting.CraftingBannerManager;
import de.mcbesser.displaygui.feature.crafting.DisplaySlotAction;
import de.mcbesser.displaygui.feature.crafting.DisplayPreset;
import de.mcbesser.displaygui.util.BlockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class DisplayGUICommand implements CommandExecutor, TabCompleter {
    private final DisplayGUIPlugin plugin;
    private final CraftingBannerManager manager;

    public DisplayGUICommand(DisplayGUIPlugin plugin, CraftingBannerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen diesen Befehl nutzen.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(Component.text("/displaygui bind <3x3|3x9|6x9|1x5>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/displaygui bind custom <x> <y>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/displaygui remove", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/displaygui title <text>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/displaygui label <slot> <text>", NamedTextColor.YELLOW));
            player.sendMessage(Component.text("/displaygui action <slot> <none|open|craft|craftmax|amountdown|amountup|nextpage|prevpage>", NamedTextColor.YELLOW));
            return true;
        }

        RayTraceResult blockTrace = player.rayTraceBlocks(8.0, FluidCollisionMode.NEVER);
        Block target = blockTrace != null ? blockTrace.getHitBlock() : null;
        var entityTrace = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 8.0,
                entity -> plugin.getDisplayEntityManager().isManagedEntity(entity));
        var targetEntity = entityTrace != null ? entityTrace.getHitEntity() : null;

        if (!args[0].equalsIgnoreCase("reload")) {
            if ((target == null || !BlockUtil.isBanner(target.getType())) && targetEntity == null) {
                player.sendMessage(Component.text("Du musst eine Banner-Flagge ansehen.", NamedTextColor.RED));
                return true;
            }
        }

        if (args[0].equalsIgnoreCase("bind")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Bitte Preset angeben: 3x3, 3x9, 6x9 oder 1x5.", NamedTextColor.RED));
                return true;
            }
            if (args[1].equalsIgnoreCase("custom")) {
                if (args.length < 4) {
                    player.sendMessage(Component.text("Bitte Breite und Hoehe angeben, z. B. /displaygui bind custom 5 4", NamedTextColor.RED));
                    return true;
                }
                int columns = parsePositive(args[2], player, "Breite");
                int rows = parsePositive(args[3], player, "Hoehe");
                if (columns < 0 || rows < 0) {
                    return true;
                }
                if (target == null || !BlockUtil.isBanner(target.getType())) {
                    player.sendMessage(Component.text("Fuer bind custom musst du eine echte Banner-Flagge ansehen.", NamedTextColor.RED));
                    return true;
                }
                manager.bindBanner(target, DisplayPreset.CUSTOM, columns, rows, player);
                return true;
            }
            DisplayPreset preset = DisplayPreset.fromId(args[1]);
            if (preset == null) {
                player.sendMessage(Component.text("Unbekanntes Preset.", NamedTextColor.RED));
                return true;
            }
            if (target == null || !BlockUtil.isBanner(target.getType())) {
                player.sendMessage(Component.text("Fuer bind musst du eine echte Banner-Flagge ansehen.", NamedTextColor.RED));
                return true;
            }
            manager.bindBanner(target, preset, player);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            boolean removed = targetEntity != null
                    ? manager.handleStandInteraction(player, targetEntity, true)
                    : manager.removeBanner(target, player);
            if (!removed) {
                player.sendMessage(Component.text("Auf dieser Banner-Flagge ist kein Display registriert.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("title")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Bitte Titel angeben.", NamedTextColor.RED));
                return true;
            }
            boolean changed = targetEntity != null
                    ? manager.setBannerTitle(targetEntity, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), player)
                    : manager.setBannerTitle(target, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)), player);
            if (!changed) {
                player.sendMessage(Component.text("Konnte Titel nicht setzen.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("label")) {
            if (args.length < 3) {
                player.sendMessage(Component.text("Bitte Slot und Text angeben.", NamedTextColor.RED));
                return true;
            }
            int slot = parseSlot(args[1], player);
            if (slot < 0) {
                return true;
            }
            boolean changed = targetEntity != null
                    ? manager.setSlotLabel(targetEntity, slot, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), player)
                    : manager.setSlotLabel(target, slot, String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), player);
            if (!changed) {
                player.sendMessage(Component.text("Konnte Slot-Beschriftung nicht setzen.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("action")) {
            if (args.length < 3) {
                player.sendMessage(Component.text("Bitte Slot und Aktion angeben.", NamedTextColor.RED));
                return true;
            }
            int slot = parseSlot(args[1], player);
            if (slot < 0) {
                return true;
            }
            DisplaySlotAction action = DisplaySlotAction.fromId(args[2]);
            if (action == null) {
                player.sendMessage(Component.text("Unbekannte Aktion.", NamedTextColor.RED));
                return true;
            }
            boolean changed = targetEntity != null
                    ? manager.setSlotAction(targetEntity, slot, action, player)
                    : manager.setSlotAction(target, slot, action, player);
            if (!changed) {
                player.sendMessage(Component.text("Konnte Klickaktion nicht setzen.", NamedTextColor.RED));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            manager.load();
            player.sendMessage(Component.text("DisplayGUI neu geladen.", NamedTextColor.GREEN));
            return true;
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("bind", "remove", "title", "label", "action", "reload");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("bind")) {
            List<String> values = new ArrayList<>();
            for (DisplayPreset preset : DisplayPreset.values()) {
                values.add(preset.id());
            }
            return values;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("action")) {
            List<String> values = new ArrayList<>();
            for (DisplaySlotAction action : DisplaySlotAction.values()) {
                values.add(action.id());
            }
            return values;
        }
        return List.of();
    }

    private int parseSlot(String raw, Player player) {
        try {
            int slot = Integer.parseInt(raw) - 1;
            if (slot < 0) {
                player.sendMessage(Component.text("Slot muss groesser als 0 sein.", NamedTextColor.RED));
                return -1;
            }
            return slot;
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text("Slot muss eine Zahl sein.", NamedTextColor.RED));
            return -1;
        }
    }

    private int parsePositive(String raw, Player player, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                player.sendMessage(Component.text(label + " muss groesser als 0 sein.", NamedTextColor.RED));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            player.sendMessage(Component.text(label + " muss eine Zahl sein.", NamedTextColor.RED));
            return -1;
        }
    }
}
