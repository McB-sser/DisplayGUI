package de.mcbesser.displaygui.feature.crafting;

public enum DisplayResultPosition {
    RIGHT("right"),
    LEFT("left"),
    TOP("top"),
    BOTTOM("bottom");

    private final String id;

    DisplayResultPosition(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DisplayResultPosition fromId(String raw) {
        for (DisplayResultPosition position : values()) {
            if (position.id.equalsIgnoreCase(raw) || position.name().equalsIgnoreCase(raw)) {
                return position;
            }
        }
        return null;
    }
}
