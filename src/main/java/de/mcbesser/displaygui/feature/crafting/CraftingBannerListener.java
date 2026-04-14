package de.mcbesser.displaygui.feature.crafting;

import de.mcbesser.displaygui.DisplayGUIPlugin;
import de.mcbesser.displaygui.util.BlockUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public final class CraftingBannerListener implements Listener {
    private final DisplayGUIPlugin plugin;
    private final CraftingBannerManager manager;

    public CraftingBannerListener(DisplayGUIPlugin plugin, CraftingBannerManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (BlockUtil.isBanner(event.getBlockPlaced().getType())) {
            manager.handleBannerPlaced(event.getBlockPlaced());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (BlockUtil.isBanner(event.getBlock().getType())) {
            manager.handleBannerBroken(event.getBlock());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBannerUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (!BlockUtil.isBanner(event.getClickedBlock().getType())) {
            return;
        }
        manager.handleBannerUse(event.getPlayer(), event.getClickedBlock(), event.getAction());
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayRightClick(PlayerInteractAtEntityEvent event) {
        if (plugin.getDisplayEntityManager().isStandEntity(event.getRightClicked())) {
            manager.handleStandInteraction(event.getPlayer(), event.getRightClicked(), false);
            event.setCancelled(true);
            return;
        }
        if (!plugin.getDisplayEntityManager().isManagedEntity(event.getRightClicked())) {
            return;
        }
        manager.handleDisplayInteraction(event.getPlayer(), event.getRightClicked(), true);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayRightClick(PlayerInteractEntityEvent event) {
        if (plugin.getDisplayEntityManager().isStandEntity(event.getRightClicked())) {
            manager.handleStandInteraction(event.getPlayer(), event.getRightClicked(), false);
            event.setCancelled(true);
            return;
        }
        if (!plugin.getDisplayEntityManager().isManagedEntity(event.getRightClicked())) {
            return;
        }
        manager.handleDisplayInteraction(event.getPlayer(), event.getRightClicked(), true);
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDisplayHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        if (plugin.getDisplayEntityManager().isStandEntity(event.getEntity())) {
            manager.handleStandInteraction(player, event.getEntity(), true);
            event.setCancelled(true);
            return;
        }
        if (!plugin.getDisplayEntityManager().isManagedEntity(event.getEntity())) {
            return;
        }
        manager.handleDisplayInteraction(player, event.getEntity(), false);
        event.setCancelled(true);
    }
}
