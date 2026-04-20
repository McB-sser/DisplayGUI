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
import java.util.Locale;
import java.util.UUID;

public final class CraftingBannerDisplay implements DisplayRenderable {
    private final CraftingBannerData data;
    private final DisplayAnchor anchor;
    private final RecipeMatch recipeMatch;
    private final FurnaceSnapshot furnaceSnapshot;

    public CraftingBannerDisplay(CraftingBannerData data, DisplayAnchor anchor, RecipeMatch recipeMatch) {
        this(data, anchor, recipeMatch, null);
    }

    public CraftingBannerDisplay(CraftingBannerData data, DisplayAnchor anchor, RecipeMatch recipeMatch, FurnaceSnapshot furnaceSnapshot) {
        this.data = data;
        this.anchor = anchor;
        this.recipeMatch = recipeMatch;
        this.furnaceSnapshot = furnaceSnapshot;
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
                return new DisplayLayout("crafting-result-only", 1, 1, 0.0, 0.55, 0.50, 1.05, 0.50f, 0.28f, 0.50f);
            }
            if (data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT) {
                return new DisplayLayout("crafting-result-amount", 3, 1, 0.0, 0.55, 0.50, 1.05, 0.50f, 0.28f, 0.50f);
            }
            if (showsResult()) {
                return switch (data.resultPosition()) {
                    case LEFT, RIGHT -> new DisplayLayout("crafting-3x3-result-" + data.resultPosition().id(), 5, 3, 0.0, 1.55, 0.50, 2.05, 0.50f, 0.28f, 0.50f);
                    case TOP, BOTTOM -> new DisplayLayout("crafting-3x3-result-" + data.resultPosition().id(), 3, 5, 0.0, 2.55, 0.50, 3.05, 0.50f, 0.28f, 0.50f);
                };
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
                        "Menge erh\u00f6hen",
                        "",
                        List.of(
                                Component.text("Linksklick: +1", NamedTextColor.WHITE),
                                Component.text("Rechtsklick: +10", NamedTextColor.WHITE)
                        )
                ));
            } else {
                int[] recipeSlots = showsResult() ? recipeSlotsWithResult() : new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
                if (showsRecipe()) {
                    for (int slot = 0; slot < recipeSlots.length; slot++) {
                        int displaySlot = recipeSlots[slot];
                        slots.add(slot(displaySlot, data.matrix().get(slot), defaultLabel(slot, "Slot " + (slot + 1))));
                    }
                }
                if (showsResult() && recipeMatch != null) {
                    addCenteredResult(slots, resultDisplaySlot());
                }
            }
        } else if (data.preset() == DisplayPreset.FURNACE_1X5) {
            slots.add(new DisplayContent.DisplaySlot(
                    0,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    snapshotItem(furnaceSnapshot == null ? null : furnaceSnapshot.input(), Material.GRAY_STAINED_GLASS_PANE),
                    amountText(furnaceSnapshot == null ? null : furnaceSnapshot.input()),
                    Component.text(defaultLabel(0, "Brennmaterial"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(new DisplayContent.DisplaySlot(
                    1,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    new ItemStack(Material.CLOCK),
                    capacityText(),
                    Component.text(defaultLabel(1, "Brenndauer"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(new DisplayContent.DisplaySlot(
                    2,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    new ItemStack(furnaceSnapshot != null && furnaceSnapshot.isBurning() ? Material.BLAZE_POWDER : Material.FIRE_CHARGE),
                    progressText(),
                    Component.text(defaultLabel(2, "Flamme"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(new DisplayContent.DisplaySlot(
                    4,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    snapshotItem(furnaceSnapshot == null ? null : furnaceSnapshot.fuel(), Material.BUCKET),
                    amountText(furnaceSnapshot == null ? null : furnaceSnapshot.fuel()),
                    Component.text(defaultLabel(4, "Brennstoff"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(new DisplayContent.DisplaySlot(
                    3,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    snapshotItem(furnaceSnapshot == null ? null : furnaceSnapshot.result(), Material.GRAY_STAINED_GLASS_PANE),
                    amountText(furnaceSnapshot == null ? null : furnaceSnapshot.result()),
                    Component.text(defaultLabel(3, "Ofen-Ergebnis"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
            slots.add(new DisplayContent.DisplaySlot(
                    5,
                    new ItemStack(Material.BLACK_STAINED_GLASS_PANE),
                    new ItemStack(Material.EXPERIENCE_BOTTLE),
                    experienceText(),
                    Component.text(defaultLabel(5, "Erfahrung"), NamedTextColor.YELLOW),
                    0.24f,
                    0.40f,
                    0.40f
            ));
        } else {
            int count = Math.min(layout().slotCount(), data.matrix().size());
            for (int slot = 0; slot < count; slot++) {
                slots.add(slot(slot, data.matrix().get(slot), defaultLabel(slot, "Slot " + (slot + 1))));
            }
        }

        String title = data.title() == null || data.title().isBlank()
                ? switch (data.preset()) {
                    case FURNACE_1X5 -> "Ofen";
                    case CRAFTING_3X3 -> "Werkbank";
                    default -> "DisplayGUI " + data.preset().id();
                }
                : data.title();
        return new DisplayContent(Component.text(title, NamedTextColor.GOLD), slots);
    }

    @Override
    public boolean supportsSidebar() {
        return data.preset() != DisplayPreset.FURNACE_1X5;
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

    private int[] recipeSlotsWithResult() {
        return switch (data.resultPosition()) {
            case RIGHT -> new int[]{0, 1, 2, 5, 6, 7, 10, 11, 12};
            case LEFT -> new int[]{2, 3, 4, 7, 8, 9, 12, 13, 14};
            case TOP -> new int[]{6, 7, 8, 9, 10, 11, 12, 13, 14};
            case BOTTOM -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
        };
    }

    private int resultDisplaySlot() {
        return switch (data.resultPosition()) {
            case RIGHT -> 9;
            case LEFT -> 5;
            case TOP -> 1;
            case BOTTOM -> 13;
        };
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

    private Component amountText(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return Component.empty();
        }
        return Component.text("x" + stack.getAmount(), NamedTextColor.WHITE);
    }

    private ItemStack snapshotItem(ItemStack stack, Material fallback) {
        return stack == null || stack.getType() == Material.AIR ? new ItemStack(fallback) : stack.clone();
    }

    private Component progressText() {
        if (furnaceSnapshot == null) {
            return Component.text("0%", NamedTextColor.WHITE);
        }
        return Component.text(furnaceSnapshot.progressPercent() + "%", NamedTextColor.WHITE);
    }

    private Component experienceText() {
        if (furnaceSnapshot == null || furnaceSnapshot.experienceAmount() <= 0.0f) {
            return Component.text("x0", NamedTextColor.WHITE);
        }
        float rounded = Math.round(furnaceSnapshot.experienceAmount() * 10.0f) / 10.0f;
        if (Math.abs(rounded - Math.round(rounded)) < 0.001f) {
            return Component.text("x" + Math.round(rounded), NamedTextColor.WHITE);
        }
        return Component.text("x" + String.format(Locale.ROOT, "%.1f", rounded), NamedTextColor.WHITE);
    }

    private Component capacityText() {
        if (furnaceSnapshot == null || furnaceSnapshot.fuelCapacity() <= 0) {
            return Component.text("x0", NamedTextColor.WHITE);
        }
        return Component.text("x" + furnaceSnapshot.fuelCapacity(), NamedTextColor.WHITE);
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

    public record FurnaceSnapshot(ItemStack input, ItemStack fuel, ItemStack result, int progressPercent, float experienceAmount, int fuelCapacity, boolean isBurning) {
    }
}
