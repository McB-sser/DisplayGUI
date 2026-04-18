package de.mcbesser.displaygui.display;

import java.util.UUID;

public interface DisplayRenderable {
    UUID uniqueId();

    DisplayAnchor anchor();

    DisplayLayout layout();

    DisplayContent content();

    default boolean isActive() {
        return true;
    }

    default boolean supportsSidebar() {
        return true;
    }
}
