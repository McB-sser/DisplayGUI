package de.mcbesser.displaygui.display;

public record DisplayLayout(
        String key,
        int columns,
        int rows,
        double originX,
        double originY,
        double slotSpacing,
        double titleOffsetY,
        float backgroundScale,
        float itemScale,
        float textScale
) {
    public int slotCount() {
        return columns * rows;
    }

    public static DisplayLayout crafting3x3() {
        return new DisplayLayout("crafting-3x3", 3, 3, -0.60, 1.15, 0.42, 1.62, 0.42f, 0.26f, 0.55f);
    }

    public static DisplayLayout chest3x9() {
        return new DisplayLayout("chest-3x9", 9, 3, -1.85, 1.45, 0.42, 1.95, 0.40f, 0.25f, 0.50f);
    }

    public static DisplayLayout doubleChest6x9() {
        return new DisplayLayout("double-chest-6x9", 9, 6, -1.85, 2.05, 0.42, 2.55, 0.40f, 0.25f, 0.50f);
    }

    public static DisplayLayout furnace1x5() {
        return new DisplayLayout("furnace-2x3", 2, 3, -0.21, 1.55, 0.50, 2.05, 0.50f, 0.28f, 0.50f);
    }
}
