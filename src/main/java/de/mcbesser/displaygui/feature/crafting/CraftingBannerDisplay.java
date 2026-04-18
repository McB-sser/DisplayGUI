package de.mcbesser.displaygui.feature.crafting;

import de.mcbesser.displaygui.display.DisplayAnchor;
import de.mcbesser.displaygui.display.DisplayContent;
import de.mcbesser.displaygui.display.DisplayLayout;
import de.mcbesser.displaygui.display.DisplayRenderable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CraftingBannerDisplay implements DisplayRenderable {
    private final CraftingBannerData data;
    private final DisplayAnchor anchor;
    private final RecipeMatch recipeMatch;

    public CraftingBannerDisplay(CraftingBannerData data, DisplayAnchor anchor, RecipeMatch recipeMatch) {
        this.data = data;
        this.anchor = anchor;
        this.recipeMatch = recipeMatch;
    }

    @Override
    public UUID uniqueId() {
        return data.id();
    }

    @Override
    public DisplayAnchor anchor() {
        return anchor;
    }

    @Override
    public DisplayLayout layout() {
        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            if (data.renderMode() == DisplayRenderMode.RESULT_ONLY) {
                return new DisplayLayout("crafting-result-only", 1, 1, 0.0, 1.55, 0.50, 2.05, 0.50f, 0.28f, 0.50f);
            }
            if (data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT) {
                return new DisplayLayout("crafting-result-amount", 3, 1, 0.0, 0.55, 0.50, 1.05, 0.50f, 0.28f, 0.50f);
            }
            if (showsResult()) {
                return new DisplayLayout("crafting-3x3-result", 5, 3, 0.0, 1.55, 0.50, 2.05, 0.50f, 0.28f, 0.50f);
            }
            return new DisplayLayout("crafting-3x3-only", 3, 3, 0.0, 1.55, 0.50, 2.05, 0.50f, 0.28f, 0.50f);
        }
        if (data.preset() == DisplayPreset.CUSTOM) {
            return new DisplayLayout(
                    "custom-" + data.customColumns() + "x" + data.customRows(),
                    data.customColumns(),
                    data.customRows(),
                    0.0,
                    1.70,
                    0.42,
                    2.15,
                    0.40f,
                    0.25f,
                    0.50f
            );
        }
        return data.preset().layout();
    }

    @Override
    public DisplayContent content() {
        List<DisplayContent.DisplaySlot> slots = new ArrayList<>();
        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            if (data.renderMode() == DisplayRenderMode.RESULT_ONLY) {
                addCenteredResult(slots, 0);
            } else if (data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT) {
                addCenteredResult(slots, 1);
                slots.add(buttonSlot(
                        0,
                        Material.RED_STAINED_GLASS_PANE,
                        "Menge verringern",
                        "",
                        List.of(
                                Component.text("Linksklick: -1", NamedTextColor.WHITE),
                                Component.text("Rechtsklick: -10", NamedTextColor.WHITE)
                        )
                ));
                slots.add(buttonSlot(
                        2,
                        Material.LIME_STAINED_GLASS_PANE,
                        "Menge erhoehen",
                        "",
                        List.of(
                                Component.text("Linksklick: +1", NamedTextColor.WHITE),
                                Component.text("Rechtsklick: +10", NamedTextColor.WHITE)
                        )
                ));
            } else {
            int[] recipeSlots = showsResult()
                    ? new int[]{0, 1, 2, 5, 6, 7, 10, 11, 12}
                    : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            if (showsRecipe()) {
                for (int slot = 0; slot < recipeSlots.length; slot++) {
                    int displaySlot = recipeSlots[slot];
                    slots.add(slot(displaySlot, data.matrix().get(slot), defaultLabel(slot, "Slot " + (slot + 1))));
                }
            }
            if (showsResult() && recipeMatch != null) {
                addCenteredResult(slots, 9);
            }
            }
        } else if (data.preset() == DisplayPreset.FURNACE_1X5) {
            slots.add(slot(0, data.matrix().get(0), defaultLabel(0, "Input")));
            slots.add(buttonSlot(1, Material.COAL, "Fuel", defaultLabel(1, "Brennstoff-Anzeige"), Collections.emptyList()));
            slots.add(buttonSlot(2, Material.BLAZE_POWDER, "->", defaultLabel(2, "Verarbeitung"), Collections.emptyList()));
            slots.add(new DisplayContent.DisplaySlot(
                    3,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    recipeMatch != null ? recipeMatch.result().clone() : new ItemStack(Material.GRAY_STAINED_GLASS_PANE),
                    recipeMatch != null ? resultAmountText() : Component.text("Kein Rezept", NamedTextColor.RED),
                    Component.text(defaultLabel(3, "Ofen-Ergebnis"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(buttonSlot(
                    4,
                    Material.SLIME_BALL,
                    "x" + data.craftAmount(),
                    defaultLabel(4, "Menge"),
                    List.of(
                            Component.text("Anzeige: Menge", NamedTextColor.YELLOW),
                            Component.text("Wert: " + data.craftAmount(), NamedTextColor.WHITE)
                    )
            ));
        } else {
            int count = Math.min(layout().slotCount(), data.matrix().size());
            for (int slot = 0; slot < count; slot++) {
                slots.add(slot(slot, data.matrix().get(slot), defaultLabel(slot, "Slot " + (slot + 1))));
            }
        }

        String title = data.title() == null || data.title().isBlank() ? "DisplayGUI " + data.preset().id() : data.title();
        return new DisplayContent(Component.text(title, NamedTextColor.GOLD), slots);
    }

    private DisplayContent.DisplaySlot slot(int slot, String materialName, String label) {
        return new DisplayContent.DisplaySlot(
                slot,
                new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                iconForMaterialName(materialName),
                Component.empty(),
                Component.text(label, NamedTextColor.YELLOW),
                0.24f,
                0.50f,
                0.50f
        );
    }

    private DisplayContent.DisplaySlot buttonSlot(int slot, Material material, String title, String label, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title, NamedTextColor.YELLOW));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            stack.setItemMeta(meta);
        }
        return new DisplayContent.DisplaySlot(
                slot,
                new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                stack,
                Component.empty(),
                Component.text(label, NamedTextColor.GRAY),
                0.24f,
                0.50f,
                0.50f
        );
    }

    private void addCenteredResult(List<DisplayContent.DisplaySlot> slots, int displaySlot) {
        if (recipeMatch == null) {
            return;
        }
        ItemStack result = resultAmountIcon();
        slots.add(new DisplayContent.DisplaySlot(
                displaySlot,
                new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                result,
                resultAmountText(),
                Component.text(defaultLabel(displaySlot, "Rezept craften"), NamedTextColor.GREEN),
                0.26f,
                0.50f,
                0.50f
        ));
    }

    private ItemStack resultAmountIcon() {
        ItemStack stack = recipeMatch.result().clone();
        stack.setAmount(Math.max(1, Math.min(data.craftAmount(), stack.getMaxStackSize())));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.lore(List.of(
                    Component.text("Menge: " + data.craftAmount(), NamedTextColor.WHITE),
                    Component.text("Linksklick: Rezept craften", NamedTextColor.WHITE),
                    Component.text("Rechtsklick: Maximal craften", NamedTextColor.WHITE)
            ));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Component resultAmountText() {
        return Component.text("x" + data.craftAmount(), NamedTextColor.WHITE);
    }

    private ItemStack iconForMaterialName(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return new ItemStack(Material.AIR);
        }
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.BARRIER;
        }
        return new ItemStack(material);
    }

    private String defaultLabel(int slot, String fallback) {
        String custom = data.slotLabels().get(slot);
        return custom == null || custom.isBlank() ? fallback : custom;
    }

    private boolean showsResult() {
        return data.renderMode() == DisplayRenderMode.RESULT_ONLY
                || data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT
                || data.renderMode() == DisplayRenderMode.RECIPE_AND_RESULT;
    }

    private boolean showsRecipe() {
        return data.renderMode() == DisplayRenderMode.RECIPE_ONLY
                || data.renderMode() == DisplayRenderMode.RECIPE_AND_RESULT;
    }
}
