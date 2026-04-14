package de.mcbesser.displaygui.feature.crafting;

import de.mcbesser.displaygui.display.DisplayLayout;

public enum DisplayPreset {
    CRAFTING_3X3("3x3", DisplayLayout.crafting3x3()),
    CHEST_3X9("3x9", DisplayLayout.chest3x9()),
    DOUBLE_CHEST_6X9("6x9", DisplayLayout.doubleChest6x9()),
    FURNACE_1X5("1x5", DisplayLayout.furnace1x5()),
    CUSTOM("custom", null);

    private final String id;
    private final DisplayLayout layout;

    DisplayPreset(String id, DisplayLayout layout) {
        this.id = id;
        this.layout = layout;
    }

    public String id() {
        return id;
    }

    public DisplayLayout layout() {
        return layout;
    }

    public static DisplayPreset fromId(String raw) {
        for (DisplayPreset preset : values()) {
            if (preset.id.equalsIgnoreCase(raw) || preset.name().equalsIgnoreCase(raw)) {
                return preset;
            }
        }
        return null;
    }
}
