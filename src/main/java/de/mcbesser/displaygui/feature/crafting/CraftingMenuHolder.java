package de.mcbesser.displaygui.feature.crafting;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class CraftingMenuHolder implements InventoryHolder {
    private final UUID bannerId;
    private final int page;
    private Inventory inventory;

    public CraftingMenuHolder(UUID bannerId, int page) {
        this.bannerId = bannerId;
        this.page = page;
    }

    public UUID bannerId() {
        return bannerId;
    }

    public int page() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
