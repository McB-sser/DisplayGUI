package de.mcbesser.displaygui.feature.crafting;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CraftingBannerData {
    private final UUID id;
    private String world;
    private int x;
    private int y;
    private int z;
    private float yaw;
    private int linkedBlockX;
    private int linkedBlockY;
    private int linkedBlockZ;
    private DisplayPreset preset;
    private int customColumns;
    private int customRows;
    private int craftAmount;
    private String title;
    private ItemStack bannerItem;
    private DisplayRenderMode renderMode;
    private DisplayResultPosition resultPosition;
    private final List<String> matrix = new ArrayList<>();
    private final List<String> slotLabels = new ArrayList<>();
    private final List<String> slotActions = new ArrayList<>();

    public CraftingBannerData(UUID id) {
        this.id = id;
        this.preset = DisplayPreset.CRAFTING_3X3;
        this.customColumns = 3;
        this.customRows = 3;
        this.craftAmount = 1;
        this.title = "DisplayGUI";
        this.linkedBlockX = Integer.MIN_VALUE;
        this.linkedBlockY = Integer.MIN_VALUE;
        this.linkedBlockZ = Integer.MIN_VALUE;
        this.bannerItem = null;
        this.renderMode = DisplayRenderMode.RECIPE_ONLY;
        this.resultPosition = DisplayResultPosition.RIGHT;
        for (int i = 0; i < 54; i++) {
            matrix.add("");
            slotLabels.add("");
            slotActions.add(DisplaySlotAction.NONE.id());
        }
    }

    public UUID id() {
        return id;
    }

    public String world() {
        return world;
    }

    public void setWorld(String world) {
        this.world = world;
    }

    public int x() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int y() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int z() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public float yaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
    }

    public int linkedBlockX() {
        return linkedBlockX;
    }

    public void setLinkedBlockX(int linkedBlockX) {
        this.linkedBlockX = linkedBlockX;
    }

    public int linkedBlockY() {
        return linkedBlockY;
    }

    public void setLinkedBlockY(int linkedBlockY) {
        this.linkedBlockY = linkedBlockY;
    }

    public int linkedBlockZ() {
        return linkedBlockZ;
    }

    public void setLinkedBlockZ(int linkedBlockZ) {
        this.linkedBlockZ = linkedBlockZ;
    }

    public boolean hasLinkedBlock() {
        return linkedBlockX != Integer.MIN_VALUE && linkedBlockY != Integer.MIN_VALUE && linkedBlockZ != Integer.MIN_VALUE;
    }

    public DisplayPreset preset() {
        return preset;
    }

    public void setPreset(DisplayPreset preset) {
        this.preset = preset;
    }

    public int craftAmount() {
        return craftAmount;
    }

    public void setCraftAmount(int craftAmount) {
        this.craftAmount = craftAmount;
    }

    public int customColumns() {
        return customColumns;
    }

    public void setCustomColumns(int customColumns) {
        this.customColumns = customColumns;
    }

    public int customRows() {
        return customRows;
    }

    public void setCustomRows(int customRows) {
        this.customRows = customRows;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ItemStack bannerItem() {
        return bannerItem == null ? null : bannerItem.clone();
    }

    public void setBannerItem(ItemStack bannerItem) {
        this.bannerItem = bannerItem == null ? null : bannerItem.clone();
    }

    public DisplayRenderMode renderMode() {
        return renderMode;
    }

    public void setRenderMode(DisplayRenderMode renderMode) {
        this.renderMode = renderMode;
    }

    public DisplayResultPosition resultPosition() {
        return resultPosition;
    }

    public void setResultPosition(DisplayResultPosition resultPosition) {
        this.resultPosition = resultPosition;
    }

    public List<String> matrix() {
        return matrix;
    }

    public List<String> slotLabels() {
        return slotLabels;
    }

    public List<String> slotActions() {
        return slotActions;
    }
}
