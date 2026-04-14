package de.mcbesser.displaygui.feature.crafting;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public final class CraftingMenuListener implements Listener {
    private final CraftingBannerManager manager;

    public CraftingMenuListener(CraftingBannerManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CraftingMenuHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        event.setCancelled(true);
        manager.handleMenuClick(player, event.getView().getTopInventory(), event.getRawSlot(), event.getClick());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CraftingMenuHolder)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            manager.onInventoryClosed(player);
        }
    }
}
