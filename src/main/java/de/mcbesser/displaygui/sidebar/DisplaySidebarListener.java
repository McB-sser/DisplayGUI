package de.mcbesser.displaygui.sidebar;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class DisplaySidebarListener implements Listener {
    private final DisplaySidebar sidebar;

    public DisplaySidebarListener(DisplaySidebar sidebar) {
        this.sidebar = sidebar;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sidebar.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        sidebar.clear(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (Float.compare(event.getFrom().getYaw(), event.getTo().getYaw()) != 0
                || Float.compare(event.getFrom().getPitch(), event.getTo().getPitch()) != 0
                || event.getFrom().distanceSquared(event.getTo()) > 0.0) {
            sidebar.refresh(event.getPlayer());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getPlayer() instanceof Player player) {
            sidebar.refresh(player);
        }
    }
}
