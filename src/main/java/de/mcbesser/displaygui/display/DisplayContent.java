package de.mcbesser.displaygui.display;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public record DisplayContent(Component title, List<DisplaySlot> slots) {
    public record DisplaySlot(
            int index,
            ItemStack background,
            ItemStack icon,
            Component amountText,
            Component label,
            float scale,
            float interactionWidth,
            float interactionHeight
    ) {
    }
}
