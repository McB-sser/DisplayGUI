package de.mcbesser.displaygui.feature.crafting;

public enum DisplayRenderMode {
    RECIPE_ONLY("recipe"),
    RESULT_ONLY("result"),
    RESULT_WITH_AMOUNT("result-amount"),
    RECIPE_AND_RESULT("recipe-result");

    private final String id;

    DisplayRenderMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DisplayRenderMode fromId(String raw) {
        for (DisplayRenderMode mode : values()) {
            if (mode.id.equalsIgnoreCase(raw) || mode.name().equalsIgnoreCase(raw)) {
                return mode;
            }
        }
        return null;
    }
}
