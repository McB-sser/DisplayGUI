package de.mcbesser.displaygui.util;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Rotatable;

public final class BlockUtil {
    private BlockUtil() {
    }

    public static boolean isBanner(Material material) {
        return Tag.BANNERS.isTagged(material) || material.name().endsWith("_BANNER") || material.name().endsWith("_WALL_BANNER");
    }

    public static float resolveYaw(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Directional directional) {
            return faceToYaw(directional.getFacing());
        }
        if (data instanceof Rotatable rotatable) {
            return faceToYaw(rotatable.getRotation());
        }
        return 180.0f;
    }

    public static float faceToYaw(BlockFace face) {
        return switch (face) {
            case NORTH -> 180.0f;
            case NORTH_NORTH_EAST -> -157.5f;
            case NORTH_EAST -> -135.0f;
            case EAST_NORTH_EAST -> -112.5f;
            case EAST -> -90.0f;
            case EAST_SOUTH_EAST -> -67.5f;
            case SOUTH_EAST -> -45.0f;
            case SOUTH_SOUTH_EAST -> -22.5f;
            case SOUTH -> 0.0f;
            case SOUTH_SOUTH_WEST -> 22.5f;
            case SOUTH_WEST -> 45.0f;
            case WEST_SOUTH_WEST -> 67.5f;
            case WEST -> 90.0f;
            case WEST_NORTH_WEST -> 112.5f;
            case NORTH_WEST -> 135.0f;
            case NORTH_NORTH_WEST -> 157.5f;
            default -> 180.0f;
        };
    }
}
