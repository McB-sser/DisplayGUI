package de.mcbesser.displaygui.feature.crafting;

public enum DisplaySlotAction {
    NONE("none"),
    OPEN_MENU("open"),
    CRAFT_ONE("craft"),
    CRAFT_MAX("craftmax"),
    AMOUNT_DOWN("amountdown"),
    AMOUNT_UP("amountup"),
    NEXT_PAGE("nextpage"),
    PREVIOUS_PAGE("prevpage");

    private final String id;

    DisplaySlotAction(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DisplaySlotAction fromId(String raw) {
        for (DisplaySlotAction action : values()) {
            if (action.id.equalsIgnoreCase(raw) || action.name().equalsIgnoreCase(raw)) {
                return action;
            }
        }
        return null;
    }
}
