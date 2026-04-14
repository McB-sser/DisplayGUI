package de.mcbesser.displaygui.display;

import org.bukkit.Location;

public record DisplayAnchor(Location location, float yaw) {
    public DisplayAnchor {
        location = location.clone();
    }
}
