package de.mcbesser.displaygui.feature.crafting;

import de.mcbesser.displaygui.DisplayGUIPlugin;
import de.mcbesser.displaygui.display.DisplayAnchor;
import de.mcbesser.displaygui.display.DisplayEntityManager;
import de.mcbesser.displaygui.display.DisplayLayout;
import de.mcbesser.displaygui.util.BlockUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.TileState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftingBannerManager {
    private static final int[] MENU_MATRIX_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 24;
    private static final int DECREASE_SLOT = 41;
    private static final int INFO_SLOT = 42;
    private static final int INCREASE_SLOT = 43;
    private static final int GENERIC_PREV_SLOT = 45;
    private static final int GENERIC_PAGE_SLOT = 49;
    private static final int GENERIC_NEXT_SLOT = 53;
    private static final int RESULT_LEFT_SLOT = 45;
    private static final int RESULT_TOP_SLOT = 46;
    private static final int RESULT_BOTTOM_SLOT = 52;
    private static final int RESULT_RIGHT_SLOT = 53;
    private static final int TITLE_EDIT_SLOT = 4;

    private final DisplayGUIPlugin plugin;
    private final DisplayEntityManager displayEntityManager;
    private final RecipeMatcher recipeMatcher = new RecipeMatcher();
    private final Map<UUID, CraftingBannerData> banners = new HashMap<>();
    private final File dataFile;
    private final NamespacedKey bannerIdKey;
    private BukkitTask refreshTask;
    private TitlePromptListener titlePromptListener;

    public CraftingBannerManager(DisplayGUIPlugin plugin, DisplayEntityManager displayEntityManager) {
        this.plugin = plugin;
        this.displayEntityManager = displayEntityManager;
        this.dataFile = new File(plugin.getDataFolder(), "banners.yml");
        this.bannerIdKey = new NamespacedKey(plugin, "banner-id");
    }

    public void load() {
        banners.clear();
        if (!dataFile.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("banners");
        if (section == null) {
            return;
        }

        for (String rawId : section.getKeys(false)) {
            ConfigurationSection entry = section.getConfigurationSection(rawId);
            if (entry == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(rawId);
                CraftingBannerData data = new CraftingBannerData(id);
                data.setWorld(entry.getString("world"));
                data.setX(entry.getInt("x"));
                data.setY(entry.getInt("y"));
                data.setZ(entry.getInt("z"));
                data.setYaw((float) entry.getDouble("yaw"));
                data.setLinkedBlockX(entry.getInt("linked-block-x", Integer.MIN_VALUE));
                data.setLinkedBlockY(entry.getInt("linked-block-y", Integer.MIN_VALUE));
                data.setLinkedBlockZ(entry.getInt("linked-block-z", Integer.MIN_VALUE));
                data.setCraftAmount(clampAmount(entry.getInt("craft-amount", 1)));
                data.setTitle(entry.getString("title", "DisplayGUI"));
                DisplayRenderMode renderMode = DisplayRenderMode.fromId(entry.getString("render-mode", DisplayRenderMode.RECIPE_ONLY.id()));
                data.setRenderMode(renderMode == null ? DisplayRenderMode.RECIPE_ONLY : renderMode);
                DisplayResultPosition resultPosition = DisplayResultPosition.fromId(entry.getString("result-position", DisplayResultPosition.RIGHT.id()));
                data.setResultPosition(resultPosition == null ? DisplayResultPosition.RIGHT : resultPosition);
                data.setCustomColumns(clampColumns(entry.getInt("custom-columns", 3)));
                data.setCustomRows(clampRows(entry.getInt("custom-rows", 3)));
                Object serializedBanner = entry.get("banner-item");
                if (serializedBanner instanceof ItemStack itemStack) {
                    data.setBannerItem(itemStack);
                }
                DisplayPreset preset = DisplayPreset.fromId(entry.getString("preset", "3x3"));
                data.setPreset(preset == null ? DisplayPreset.CRAFTING_3X3 : preset);
                List<String> matrix = entry.getStringList("matrix");
                for (int i = 0; i < Math.min(54, matrix.size()); i++) {
                    data.matrix().set(i, matrix.get(i));
                }
                List<String> labels = entry.getStringList("labels");
                for (int i = 0; i < Math.min(54, labels.size()); i++) {
                    data.slotLabels().set(i, labels.get(i));
                }
                List<String> actions = entry.getStringList("actions");
                for (int i = 0; i < Math.min(54, actions.size()); i++) {
                    DisplaySlotAction action = DisplaySlotAction.fromId(actions.get(i));
                    data.slotActions().set(i, (action == null ? DisplaySlotAction.NONE : action).id());
                }
                normalizeMatrixForPreset(data);
                banners.put(id, data);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void start() {
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAllDisplays, 20L, 20L);
        Bukkit.getScheduler().runTask(plugin, this::refreshAllDisplays);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        save();
        for (UUID id : new ArrayList<>(banners.keySet())) {
            displayEntityManager.unregister(id);
        }
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        for (CraftingBannerData data : banners.values()) {
            String path = "banners." + data.id();
            config.set(path + ".world", data.world());
            config.set(path + ".x", data.x());
            config.set(path + ".y", data.y());
            config.set(path + ".z", data.z());
            config.set(path + ".yaw", data.yaw());
            config.set(path + ".linked-block-x", data.hasLinkedBlock() ? data.linkedBlockX() : null);
            config.set(path + ".linked-block-y", data.hasLinkedBlock() ? data.linkedBlockY() : null);
            config.set(path + ".linked-block-z", data.hasLinkedBlock() ? data.linkedBlockZ() : null);
            config.set(path + ".preset", data.preset().id());
            config.set(path + ".custom-columns", data.customColumns());
            config.set(path + ".custom-rows", data.customRows());
            config.set(path + ".craft-amount", data.craftAmount());
            config.set(path + ".title", data.title());
            config.set(path + ".render-mode", data.renderMode().id());
            config.set(path + ".result-position", data.resultPosition().id());
            config.set(path + ".banner-item", data.bannerItem());
            config.set(path + ".matrix", data.matrix());
            config.set(path + ".labels", data.slotLabels());
            config.set(path + ".actions", data.slotActions());
        }
        try {
            dataFile.getParentFile().mkdirs();
            config.save(dataFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("Konnte banners.yml nicht speichern: " + exception.getMessage());
        }
    }

    public Collection<CraftingBannerData> allBanners() {
        return banners.values();
    }

    public DisplayGUIPlugin getPlugin() {
        return plugin;
    }

    public void setTitlePromptListener(TitlePromptListener titlePromptListener) {
        this.titlePromptListener = titlePromptListener;
    }

    public boolean bindBanner(Block block, DisplayPreset preset, Player actor) {
        return bindBanner(block, preset, 3, 3, actor);
    }

    public boolean bindBanner(Block block, DisplayPreset preset, int columns, int rows, Player actor) {
        if (!BlockUtil.isBanner(block.getType())) {
            return false;
        }
        UUID existingBannerId = getBannerId(block);
        UUID bannerId = existingBannerId == null ? UUID.randomUUID() : existingBannerId;
        Block linkedFurnace = preset == DisplayPreset.FURNACE_1X5 ? findLinkedFurnaceBlock(block) : null;
        if (preset == DisplayPreset.FURNACE_1X5 && linkedFurnace == null) {
            if (actor != null) {
                actor.sendMessage(Component.text("Kein passender Ofen f\u00fcr dieses Banner gefunden.", NamedTextColor.RED));
            }
            displayEntityManager.unregister(bannerId);
            return false;
        }
        if (preset == DisplayPreset.FURNACE_1X5 && isLinkedToOtherBanner(linkedFurnace, bannerId)) {
            if (actor != null) {
                actor.sendMessage(Component.text("Dieser Ofen ist bereits mit einem anderen Display verbunden.", NamedTextColor.RED));
            }
            displayEntityManager.unregister(bannerId);
            return false;
        }
        ensureBannerId(block, bannerId);
        CraftingBannerData data = banners.computeIfAbsent(bannerId, CraftingBannerData::new);
        if (data.bannerItem() == null) {
            data.setBannerItem(new ItemStack(block.getType()));
        }
        data.setWorld(block.getWorld().getName());
        data.setX(block.getX());
        data.setY(block.getY());
        data.setZ(block.getZ());
        data.setYaw(BlockUtil.resolveYaw(block));
        data.setPreset(preset);
        linkPresetTarget(data, block);
        data.setCustomColumns(clampColumns(columns));
        data.setCustomRows(clampRows(rows));
        if (data.title() == null || data.title().isBlank() || data.title().equals("DisplayGUI")) {
            data.setTitle(defaultTitle(data));
        }
        normalizeMatrixForPreset(data);
        if (block.getType() != Material.AIR) {
            block.setType(Material.AIR, false);
        }
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Display an Banner gebunden: " + presetName(data), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean removeBanner(Block block, Player actor) {
        UUID bannerId = getBannerId(block);
        if (bannerId == null) {
            return false;
        }
        banners.remove(bannerId);
        displayEntityManager.unregister(bannerId);
        save();
        if (actor != null) {
            actor.sendMessage(Component.text("Display entfernt.", NamedTextColor.YELLOW));
        }
        return true;
    }

    public void handleBannerPlaced(Block block) {
        if (!BlockUtil.isBanner(block.getType())) {
            return;
        }
        Material below = block.getRelative(0, -1, 0).getType();
        if (below == Material.CRAFTING_TABLE) {
            bindBanner(block, DisplayPreset.CRAFTING_3X3, null);
        } else if (findLinkedFurnaceBlock(block) != null) {
            bindBanner(block, DisplayPreset.FURNACE_1X5, null);
        }
    }

    public void handleBannerBroken(Block block) {
        UUID bannerId = getBannerId(block);
        if (bannerId == null) {
            return;
        }
        banners.remove(bannerId);
        displayEntityManager.unregister(bannerId);
        save();
    }

    public void handleBannerUse(Player player, Block block, Action action) {
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        UUID bannerId = getBannerId(block);
        if (bannerId == null) {
            return;
        }
        openCraftingMenu(player, bannerId);
    }

    public boolean handleStandInteraction(Player player, Entity entity, boolean breakAction) {
        UUID displayId = displayEntityManager.getDisplayId(entity);
        if (displayId == null) {
            return false;
        }
        if (breakAction) {
            return destroyStandAndDrop(displayId, player);
        }
        openCraftingMenu(player, displayId);
        return true;
    }

    public boolean handleDisplayInteraction(Player player, Entity entity, boolean rightClick) {
        UUID displayId = displayEntityManager.getDisplayId(entity);
        int slot = displayEntityManager.getSlot(entity);
        if (displayId == null || slot < 0) {
            return false;
        }

        CraftingBannerData data = banners.get(displayId);
        if (data == null) {
            return false;
        }

        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            if (data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT) {
                if (slot == 0) {
                    adjustAmount(data, rightClick ? -10 : -1);
                    return true;
                }
                if (slot == 2) {
                    adjustAmount(data, rightClick ? 10 : 1);
                    return true;
                }
                if (slot == 1) {
                    craftConfiguredRecipe(player, data, rightClick);
                    return true;
                }
            }
            if (slot == 6) {
                adjustAmount(data, rightClick ? -10 : -1);
                return true;
            }
            if (slot == 8) {
                adjustAmount(data, rightClick ? 10 : 1);
                return true;
            }
            if (slot == craftingResultSlot(data)) {
                craftConfiguredRecipe(player, data, rightClick);
                return true;
            }
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            if (handleFurnaceDisplayInteraction(player, data, slot, rightClick)) {
                refreshDisplay(data);
                return true;
            }
        }

        if (executeConfiguredSlotAction(player, data, slot, rightClick)) {
            return true;
        }

        openCraftingMenu(player, data.id());
        return true;
    }

    public void openCraftingMenu(Player player, UUID bannerId) {
        CraftingBannerData data = banners.get(bannerId);
        if (data == null) {
            return;
        }

        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            openCrafting3x3Menu(player, data);
            return;
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            openFurnaceMenu(player, data);
            return;
        }
        openGenericGridMenu(player, data, 0);
    }

    private void openCrafting3x3Menu(Player player, CraftingBannerData data) {
        CraftingMenuHolder holder = new CraftingMenuHolder(data.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(data.title()));
        holder.setInventory(inventory);
        populateCrafting3x3Inventory(inventory, data);
        player.openInventory(inventory);
    }

    private void openGenericGridMenu(Player player, CraftingBannerData data, int page) {
        int displaySlots = getDisplaySlotCount(data);
        int editablePerPage = 45;
        int maxPage = Math.max(0, (displaySlots - 1) / editablePerPage);
        int currentPage = Math.max(0, Math.min(page, maxPage));
        CraftingMenuHolder holder = new CraftingMenuHolder(data.id(), currentPage);
        Inventory inventory = Bukkit.createInventory(holder, 54, Component.text(data.title()));
        holder.setInventory(inventory);
        populateGenericGridInventory(inventory, data, currentPage);
        player.openInventory(inventory);
    }

    private void populateCrafting3x3Inventory(Inventory inventory, CraftingBannerData data) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        int[] centeredMatrixSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        for (int i = 0; i < centeredMatrixSlots.length; i++) {
            inventory.setItem(centeredMatrixSlots[i], recipeMenuItem(data.matrix().get(i)));
        }

        inventory.setItem(4, named(Material.WRITABLE_BOOK, "Titel bearbeiten"));
        inventory.setItem(39, named(Material.RED_STAINED_GLASS_PANE, "Menge verringern", List.of(
                Component.text("Linksklick: -1", NamedTextColor.WHITE),
                Component.text("Rechtsklick: -10", NamedTextColor.WHITE)
        )));
        inventory.setItem(40, named(Material.SLIME_BALL, "Menge: " + data.craftAmount()));
        inventory.setItem(41, named(Material.LIME_STAINED_GLASS_PANE, "Menge erh\u00f6hen", List.of(
                Component.text("Linksklick: +1", NamedTextColor.WHITE),
                Component.text("Rechtsklick: +10", NamedTextColor.WHITE)
        )));
        inventory.setItem(17, modeItem(Material.PAPER, "Anzeige Rezept", data.renderMode() == DisplayRenderMode.RECIPE_ONLY));
        inventory.setItem(26, modeItem(Material.CRAFTING_TABLE, "Anzeige Rezept + Ergebnis", data.renderMode() == DisplayRenderMode.RECIPE_AND_RESULT));
        inventory.setItem(35, modeItem(Material.CHEST, "Anzeige Ergebnis", data.renderMode() == DisplayRenderMode.RESULT_ONLY));
        inventory.setItem(44, modeItem(Material.HONEY_BOTTLE, "Anzeige Ergebnis + Menge", data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT));
        inventory.setItem(RESULT_LEFT_SLOT, positionItem(Material.ARROW, "Ergebnis links", data.resultPosition() == DisplayResultPosition.LEFT));
        inventory.setItem(RESULT_TOP_SLOT, positionItem(Material.SPECTRAL_ARROW, "Ergebnis oben", data.resultPosition() == DisplayResultPosition.TOP));
        inventory.setItem(RESULT_BOTTOM_SLOT, positionItem(Material.ARROW, "Ergebnis unten", data.resultPosition() == DisplayResultPosition.BOTTOM));
        inventory.setItem(RESULT_RIGHT_SLOT, positionItem(Material.SPECTRAL_ARROW, "Ergebnis rechts", data.resultPosition() == DisplayResultPosition.RIGHT));

        RecipeMatch match = findRecipeMatch(data);
        if (match != null) {
            ItemStack result = match.result().clone();
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Ergebnis", NamedTextColor.GREEN));
                result.setItemMeta(meta);
            }
            inventory.setItem(25, result);
        }
    }

    private void populateGenericGridInventory(Inventory inventory, CraftingBannerData data, int currentPage) {
        int displaySlots = getDisplaySlotCount(data);
        int editablePerPage = 45;
        int maxPage = Math.max(0, (displaySlots - 1) / editablePerPage);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        int start = currentPage * editablePerPage;
        int end = Math.min(displaySlots, start + editablePerPage);
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, itemFromName(data.matrix().get(i)));
        }
        inventory.setItem(GENERIC_PREV_SLOT, named(Material.ARROW, "Seite zur\u00fcck"));
        inventory.setItem(GENERIC_PAGE_SLOT, named(Material.BOOK, "Seite " + (currentPage + 1) + "/" + (maxPage + 1)));
        inventory.setItem(GENERIC_NEXT_SLOT, named(Material.ARROW, "Seite vor"));
    }

    private void openFurnaceMenu(Player player, CraftingBannerData data) {
        CraftingMenuHolder holder = new CraftingMenuHolder(data.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 9, Component.text(data.title()));
        holder.setInventory(inventory);
        populateFurnaceInventory(inventory, data);
        player.openInventory(inventory);
    }

    private void populateFurnaceInventory(Inventory inventory, CraftingBannerData data) {
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        inventory.setItem(TITLE_EDIT_SLOT, named(Material.WRITABLE_BOOK, "Titel bearbeiten"));
    }

    public void handleMenuClick(Player player, Inventory inventory, int rawSlot, ClickType click) {
        if (!(inventory.getHolder() instanceof CraftingMenuHolder holder)) {
            return;
        }
        CraftingBannerData data = banners.get(holder.bannerId());
        if (data == null) {
            player.closeInventory();
            return;
        }

        if ((data.preset() == DisplayPreset.CRAFTING_3X3 || data.preset() == DisplayPreset.FURNACE_1X5) && rawSlot == TITLE_EDIT_SLOT) {
            if (titlePromptListener != null) {
                titlePromptListener.requestTitle(player, data.id());
            }
            return;
        }
        if (data.preset() == DisplayPreset.CHEST_3X9 || data.preset() == DisplayPreset.DOUBLE_CHEST_6X9) {
            handleGenericGridMenuClick(player, data, rawSlot, holder.page());
            return;
        }
        if (data.preset() == DisplayPreset.CUSTOM) {
            handleGenericGridMenuClick(player, data, rawSlot, holder.page());
            return;
        }
        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            if (rawSlot == 17) {
                setRenderMode(data, DisplayRenderMode.RECIPE_ONLY);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == 26) {
                setRenderMode(data, DisplayRenderMode.RECIPE_AND_RESULT);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == 35) {
                setRenderMode(data, DisplayRenderMode.RESULT_ONLY);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == 44) {
                setRenderMode(data, DisplayRenderMode.RESULT_WITH_AMOUNT);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == RESULT_LEFT_SLOT) {
                setResultPosition(data, DisplayResultPosition.LEFT);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == RESULT_TOP_SLOT) {
                setResultPosition(data, DisplayResultPosition.TOP);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == RESULT_BOTTOM_SLOT) {
                setResultPosition(data, DisplayResultPosition.BOTTOM);
                openCraftingMenu(player, data.id());
                return;
            }
            if (rawSlot == RESULT_RIGHT_SLOT) {
                setResultPosition(data, DisplayResultPosition.RIGHT);
                openCraftingMenu(player, data.id());
                return;
            }
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            handleFurnaceMenuClick(player, data, rawSlot);
            return;
        }

        if (rawSlot == 39) {
            adjustAmount(data, click.isRightClick() ? -10 : -1);
            openCraftingMenu(player, data.id());
            return;
        }
        if (rawSlot == 41) {
            adjustAmount(data, click.isRightClick() ? 10 : 1);
            openCraftingMenu(player, data.id());
            return;
        }
        if (rawSlot == 39 || rawSlot == 41) {
            return;
        }
        if (rawSlot == 25) {
            craftConfiguredRecipe(player, data, click.isRightClick());
            openCraftingMenu(player, data.id());
            return;
        }

        int[] centeredMatrixSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        for (int i = 0; i < centeredMatrixSlots.length; i++) {
            if (rawSlot != centeredMatrixSlots[i]) {
                continue;
            }
            ItemStack cursor = player.getItemOnCursor();
            data.matrix().set(i, cursor == null || cursor.getType() == Material.AIR ? "" : cursor.getType().name());
            save();
            refreshDisplay(data);
            populateCrafting3x3Inventory(inventory, data);
            return;
        }
    }

    private void handleGenericGridMenuClick(Player player, CraftingBannerData data, int rawSlot, int page) {
        int slotCount = getDisplaySlotCount(data);
        int editablePerPage = 45;
        int start = page * editablePerPage;
        int maxPage = Math.max(0, (slotCount - 1) / editablePerPage);
        if (rawSlot == GENERIC_PREV_SLOT) {
            openGenericGridMenu(player, data, Math.max(0, page - 1));
            return;
        }
        if (rawSlot == GENERIC_NEXT_SLOT) {
            openGenericGridMenu(player, data, Math.min(maxPage, page + 1));
            return;
        }
        if (rawSlot < 0 || rawSlot >= editablePerPage) {
            return;
        }
        int actualSlot = start + rawSlot;
        if (actualSlot >= slotCount) {
            return;
        }
        ItemStack cursor = player.getItemOnCursor();
        data.matrix().set(actualSlot, cursor == null || cursor.getType() == Material.AIR ? "" : cursor.getType().name());
        save();
        refreshDisplay(data);
        populateGenericGridInventory(player.getOpenInventory().getTopInventory(), data, page);
    }

    private void handleFurnaceMenuClick(Player player, CraftingBannerData data, int rawSlot) {
        if (rawSlot == TITLE_EDIT_SLOT && titlePromptListener != null) {
            titlePromptListener.requestTitle(player, data.id());
        }
    }

    public void onInventoryClosed(Player player) {
    }

    public boolean setBannerTitle(Block block, String title, Player actor) {
        CraftingBannerData data = getBannerData(block);
        if (data == null) {
            return false;
        }
        data.setTitle(title == null || title.isBlank() ? "DisplayGUI " + data.preset().id() : title);
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Titel gesetzt: " + data.title(), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setBannerTitle(Entity entity, String title, Player actor) {
        CraftingBannerData data = getBannerData(entity);
        if (data == null) {
            return false;
        }
        data.setTitle(title == null || title.isBlank() ? defaultTitle(data) : title);
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Titel gesetzt: " + data.title(), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setBannerTitle(UUID bannerId, String title, Player actor) {
        CraftingBannerData data = banners.get(bannerId);
        if (data == null) {
            return false;
        }
        data.setTitle(title == null || title.isBlank() ? defaultTitle(data) : title);
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Titel gesetzt: " + data.title(), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setSlotLabel(Block block, int slot, String label, Player actor) {
        CraftingBannerData data = getBannerData(block);
        if (data == null || !isValidPresetSlot(data, slot)) {
            return false;
        }
        data.slotLabels().set(slot, label == null ? "" : label);
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Beschriftung f\u00fcr Slot " + (slot + 1) + " gesetzt.", NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setSlotLabel(Entity entity, int slot, String label, Player actor) {
        CraftingBannerData data = getBannerData(entity);
        if (data == null || !isValidPresetSlot(data, slot)) {
            return false;
        }
        data.slotLabels().set(slot, label == null ? "" : label);
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Beschriftung f\u00fcr Slot " + (slot + 1) + " gesetzt.", NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setSlotAction(Block block, int slot, DisplaySlotAction action, Player actor) {
        CraftingBannerData data = getBannerData(block);
        if (data == null || !isValidPresetSlot(data, slot)) {
            return false;
        }
        data.slotActions().set(slot, (action == null ? DisplaySlotAction.NONE : action).id());
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Klickaktion f\u00fcr Slot " + (slot + 1) + " ist jetzt " + data.slotActions().get(slot), NamedTextColor.GREEN));
        }
        return true;
    }

    public boolean setSlotAction(Entity entity, int slot, DisplaySlotAction action, Player actor) {
        CraftingBannerData data = getBannerData(entity);
        if (data == null || !isValidPresetSlot(data, slot)) {
            return false;
        }
        data.slotActions().set(slot, (action == null ? DisplaySlotAction.NONE : action).id());
        save();
        refreshDisplay(data);
        if (actor != null) {
            actor.sendMessage(Component.text("Klickaktion f\u00fcr Slot " + (slot + 1) + " ist jetzt " + data.slotActions().get(slot), NamedTextColor.GREEN));
        }
        return true;
    }

    private void craftConfiguredRecipe(Player player, CraftingBannerData data, boolean maxMode) {
        RecipeMatch match = findRecipeMatch(data);
        if (match == null) {
            player.sendMessage(Component.text("Kein g\u00fcltiges Rezept vorhanden.", NamedTextColor.RED));
            return;
        }

        int requestedCrafts = maxMode ? 64 : data.craftAmount();
        int maxPossible = maxCraftsPossible(player.getInventory(), match);
        int crafts = Math.min(requestedCrafts, maxPossible);
        if (crafts <= 0) {
            player.sendMessage(Component.text("Nicht genug Materialien im Inventar.", NamedTextColor.RED));
            return;
        }

        consumeIngredients(player.getInventory(), match, crafts);
        ItemStack result = match.result().clone();
        int totalAmount = result.getAmount() * crafts;
        while (totalAmount > 0) {
            ItemStack give = result.clone();
            give.setAmount(Math.min(give.getMaxStackSize(), totalAmount));
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(give);
            for (ItemStack rest : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
            totalAmount -= give.getAmount();
        }
        player.sendMessage(Component.text("Gecraftet: " + crafts + "x " + result.getType(), NamedTextColor.GREEN));
    }

    private int maxCraftsPossible(PlayerInventory inventory, RecipeMatch match) {
        Map<Material, Integer> costs = new HashMap<>();
        for (ItemStack ingredient : match.normalizedIngredients().values()) {
            costs.merge(ingredient.getType(), 1, Integer::sum);
        }

        int max = Integer.MAX_VALUE;
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            int available = countMaterial(inventory, entry.getKey());
            max = Math.min(max, available / entry.getValue());
        }
        return max == Integer.MAX_VALUE ? 0 : max;
    }

    private int countMaterial(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private void consumeIngredients(PlayerInventory inventory, RecipeMatch match, int crafts) {
        Map<Material, Integer> costs = new HashMap<>();
        for (ItemStack ingredient : match.normalizedIngredients().values()) {
            costs.merge(ingredient.getType(), crafts, Integer::sum);
        }

        ItemStack[] contents = inventory.getStorageContents();
        for (Map.Entry<Material, Integer> entry : costs.entrySet()) {
            int remaining = entry.getValue();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack stack = contents[i];
                if (stack == null || stack.getType() != entry.getKey()) {
                    continue;
                }
                int taken = Math.min(stack.getAmount(), remaining);
                stack.setAmount(stack.getAmount() - taken);
                remaining -= taken;
                if (stack.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        inventory.setStorageContents(contents);
    }

    private void adjustAmount(CraftingBannerData data, int delta) {
        data.setCraftAmount(clampAmount(data.craftAmount() + delta));
        save();
        refreshDisplay(data);
    }

    private int clampAmount(int value) {
        int max = Math.max(1, plugin.getConfig().getInt("crafting.max-amount", 64));
        return Math.max(1, Math.min(max, value));
    }

    private void refreshAllDisplays() {
        for (CraftingBannerData data : new ArrayList<>(banners.values())) {
            refreshDisplay(data);
        }
    }

    private void refreshDisplay(CraftingBannerData data) {
        Block block = resolveBlock(data);
        if (block == null) {
            displayEntityManager.unregister(data.id());
            return;
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5 && !isPrimaryLinkedBanner(data)) {
            displayEntityManager.unregister(data.id());
            return;
        }
        RecipeMatch match = findRecipeMatch(data);
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            displayEntityManager.register(new CraftingBannerDisplay(
                    data,
                    new DisplayAnchor(block.getLocation(), data.yaw()),
                    match,
                    captureFurnaceSnapshot(data)
            ));
            return;
        }
        displayEntityManager.register(new CraftingBannerDisplay(data, new DisplayAnchor(block.getLocation(), data.yaw()), match));
    }

    private RecipeMatch findRecipeMatch(CraftingBannerData data) {
        ItemStack[] matrix = new ItemStack[9];
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            Furnace furnace = resolveFurnace(data);
            FurnaceInventory inventory = furnace == null ? null : furnace.getInventory();
            ItemStack input = inventory == null ? null : inventory.getSmelting();
            return recipeMatcher.adaptCookingRecipe(input, data.preset(), resolveCookerType(data));
        }
        for (int i = 0; i < 9; i++) {
            matrix[i] = itemFromName(data.matrix().get(i));
        }
        return recipeMatcher.findMatchingCraftingRecipe(matrix);
    }

    private CraftingBannerDisplay.FurnaceSnapshot captureFurnaceSnapshot(CraftingBannerData data) {
        Furnace furnace = resolveFurnace(data);
        if (furnace == null) {
            return new CraftingBannerDisplay.FurnaceSnapshot(null, null, null, 0, 0.0f, 0, false);
        }
        FurnaceInventory inventory = furnace.getInventory();
        int totalCookTime = Math.max(1, furnace.getCookTimeTotal());
        int progressPercent = Math.max(0, Math.min(100, Math.round((furnace.getCookTime() * 100.0f) / totalCookTime)));
        float experienceAmount = captureStoredFurnaceExperience(furnace, inventory.getSmelting(), inventory.getResult(), furnace.getBlock().getType());
        int fuelCapacity = captureFuelCapacity(furnace, inventory.getFuel());
        return new CraftingBannerDisplay.FurnaceSnapshot(
                cloneOrNull(inventory.getSmelting()),
                cloneOrNull(inventory.getFuel()),
                cloneOrNull(inventory.getResult()),
                progressPercent,
                experienceAmount,
                fuelCapacity,
                furnace.getBurnTime() > 0
        );
    }

    private float captureStoredFurnaceExperience(Furnace furnace, ItemStack input, ItemStack result, Material cookerType) {
        return reflectStoredFurnaceExperience(furnace);
    }

    private float reflectStoredFurnaceExperience(Furnace furnace) {
        try {
            Method method = furnace.getClass().getMethod("getRecipesUsed");
            Object value = method.invoke(furnace);
            if (!(value instanceof Map<?, ?> recipesUsed)) {
                return 0.0f;
            }
            float total = 0.0f;
            for (Map.Entry<?, ?> entry : recipesUsed.entrySet()) {
                if (!(entry.getKey() instanceof CookingRecipe<?> recipe) || !(entry.getValue() instanceof Number count)) {
                    continue;
                }
                total += recipe.getExperience() * count.intValue();
            }
            return total;
        } catch (ReflectiveOperationException ignored) {
            return 0.0f;
        }
    }

    private int captureFuelCapacity(Furnace furnace, ItemStack fuelStack) {
        RecipeMatch currentRecipe = recipeMatcher.adaptCookingRecipe(furnace.getInventory().getSmelting(), DisplayPreset.FURNACE_1X5, furnace.getBlock().getType());
        Recipe recipe = currentRecipe == null ? null : currentRecipe.recipe();
        int cookTimeTotal = recipe instanceof CookingRecipe<?> cookingRecipe ? Math.max(1, cookingRecipe.getCookingTime()) : Math.max(1, furnace.getCookTimeTotal());
        int burnTicks = Math.max(0, furnace.getBurnTime());
        burnTicks += fuelBurnTicks(fuelStack);
        return burnTicks / cookTimeTotal;
    }

    private int fuelBurnTicks(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return 0;
        }
        int singleFuelTicks = reflectMaterialBurnTime(stack.getType());
        return Math.max(0, singleFuelTicks) * stack.getAmount();
    }

    private int reflectMaterialBurnTime(Material material) {
        try {
            Method method = material.getClass().getMethod("getBurnTime");
            Object value = method.invoke(material);
            return value instanceof Number number ? number.intValue() : 0;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private Block resolveBlock(CraftingBannerData data) {
        World world = Bukkit.getWorld(data.world());
        return world == null ? null : world.getBlockAt(data.x(), data.y(), data.z());
    }

    private Furnace resolveFurnace(CraftingBannerData data) {
        Block furnaceBlock = resolveLinkedBlock(data);
        if (furnaceBlock == null) {
            Block anchorBlock = resolveBlock(data);
            if (anchorBlock == null) {
                return null;
            }
            furnaceBlock = anchorBlock.getRelative(0, -1, 0);
        }
        BlockState state = furnaceBlock.getState();
        return state instanceof Furnace furnace ? furnace : null;
    }

    private Block resolveLinkedBlock(CraftingBannerData data) {
        if (!data.hasLinkedBlock()) {
            return null;
        }
        World world = Bukkit.getWorld(data.world());
        return world == null ? null : world.getBlockAt(data.linkedBlockX(), data.linkedBlockY(), data.linkedBlockZ());
    }

    private Material resolveCookerType(CraftingBannerData data) {
        Furnace furnace = resolveFurnace(data);
        return furnace == null ? Material.FURNACE : furnace.getBlock().getType();
    }

    private void linkPresetTarget(CraftingBannerData data, Block bannerBlock) {
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            Block linkedFurnace = findLinkedFurnaceBlock(bannerBlock);
            if (linkedFurnace != null) {
                data.setLinkedBlockX(linkedFurnace.getX());
                data.setLinkedBlockY(linkedFurnace.getY());
                data.setLinkedBlockZ(linkedFurnace.getZ());
            }
            return;
        }
        data.setLinkedBlockX(Integer.MIN_VALUE);
        data.setLinkedBlockY(Integer.MIN_VALUE);
        data.setLinkedBlockZ(Integer.MIN_VALUE);
    }

    private boolean isLinkedToOtherBanner(Block block, UUID currentBannerId) {
        if (block == null) {
            return false;
        }
        for (CraftingBannerData other : banners.values()) {
            if (other.id().equals(currentBannerId) || !other.hasLinkedBlock()) {
                continue;
            }
            if (other.linkedBlockX() == block.getX()
                    && other.linkedBlockY() == block.getY()
                    && other.linkedBlockZ() == block.getZ()
                    && other.world() != null
                    && other.world().equals(block.getWorld().getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPrimaryLinkedBanner(CraftingBannerData data) {
        if (data.preset() != DisplayPreset.FURNACE_1X5 || !data.hasLinkedBlock()) {
            return true;
        }
        CraftingBannerData winner = data;
        for (CraftingBannerData other : banners.values()) {
            if (other.id().equals(data.id()) || !other.hasLinkedBlock()) {
                continue;
            }
            if (!sameLinkedBlock(data, other)) {
                continue;
            }
            if (other.id().toString().compareTo(winner.id().toString()) < 0) {
                winner = other;
            }
        }
        return winner.id().equals(data.id());
    }

    private boolean sameLinkedBlock(CraftingBannerData left, CraftingBannerData right) {
        return left.world() != null
                && left.world().equals(right.world())
                && left.linkedBlockX() == right.linkedBlockX()
                && left.linkedBlockY() == right.linkedBlockY()
                && left.linkedBlockZ() == right.linkedBlockZ();
    }

    private Block findLinkedFurnaceBlock(Block bannerBlock) {
        List<Block> candidates = new ArrayList<>();
        candidates.add(bannerBlock.getRelative(0, -1, 0));
        candidates.add(bannerBlock.getRelative(BlockFace.NORTH));
        candidates.add(bannerBlock.getRelative(BlockFace.EAST));
        candidates.add(bannerBlock.getRelative(BlockFace.SOUTH));
        candidates.add(bannerBlock.getRelative(BlockFace.WEST));

        Block best = null;
        int bestScore = Integer.MIN_VALUE;
        BlockFace bannerFacing = BlockUtil.resolveFacing(bannerBlock);
        for (Block candidate : candidates) {
            if (!isFurnaceType(candidate.getType())) {
                continue;
            }
            int score = furnaceLinkScore(candidate, bannerBlock, bannerFacing);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private int furnaceLinkScore(Block furnaceBlock, Block bannerBlock, BlockFace bannerFacing) {
        int score = 0;
        if (furnaceBlock.getRelative(BlockFace.UP).equals(bannerBlock)) {
            score += 200;
        }
        if (furnaceBlock.getY() == bannerBlock.getY()) {
            score += 20;
        }
        BlockFace furnaceFacing = BlockUtil.resolveFacing(furnaceBlock);
        if (furnaceBlock.getRelative(furnaceFacing).equals(bannerBlock)) {
            score += 160;
        }
        if (furnaceFacing == bannerFacing) {
            score += 80;
        }
        if (bannerBlock.getRelative(bannerFacing.getOppositeFace()).equals(furnaceBlock)) {
            score += 40;
        }
        return score;
    }

    private boolean isFurnaceType(Material material) {
        return material == Material.FURNACE || material == Material.BLAST_FURNACE || material == Material.SMOKER;
    }

    private UUID getOrCreateBannerId(Block block) {
        UUID existing = getBannerId(block);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        ensureBannerId(block, created);
        return created;
    }

    private void ensureBannerId(Block block, UUID bannerId) {
        BlockState state = block.getState();
        if (state instanceof Banner banner) {
            banner.getPersistentDataContainer().set(bannerIdKey, PersistentDataType.STRING, bannerId.toString());
            banner.update(true, false);
        }
    }

    private UUID getBannerId(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Banner banner)) {
            return null;
        }
        String raw = banner.getPersistentDataContainer().get(bannerIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ItemStack itemFromName(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        Material material = Material.matchMaterial(materialName);
        return material == null ? new ItemStack(Material.BARRIER) : new ItemStack(material);
    }

    private ItemStack recipeMenuItem(String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return named(Material.GREEN_STAINED_GLASS_PANE, "Rezeptslot");
        }
        return itemFromName(materialName);
    }

    private void normalizeMatrixForPreset(CraftingBannerData data) {
        int usedSlots = getDisplaySlotCount(data);
        for (int i = usedSlots; i < data.matrix().size(); i++) {
            data.matrix().set(i, "");
            data.slotLabels().set(i, "");
            data.slotActions().set(i, DisplaySlotAction.NONE.id());
        }

        if (data.preset() == DisplayPreset.CUSTOM && (data.title() == null || data.title().isBlank() || data.title().equals("DisplayGUI"))) {
            data.setTitle(defaultTitle(data));
        }
    }

    private ItemStack named(Material material, String name) {
        return named(material, name, List.of());
    }

    private ItemStack named(Material material, String name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
            if (lore != null && !lore.isEmpty()) {
                meta.lore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack modeItem(Material material, String name, boolean enabled) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name + ": " + (enabled ? "AN" : "AUS"),
                    enabled ? NamedTextColor.GREEN : NamedTextColor.RED));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack positionItem(Material material, String name, boolean enabled) {
        return modeItem(material, name, enabled);
    }

    private CraftingBannerData getBannerData(Block block) {
        UUID bannerId = getBannerId(block);
        return bannerId == null ? null : banners.get(bannerId);
    }

    public CraftingBannerData getBannerData(Entity entity) {
        UUID bannerId = displayEntityManager.getDisplayId(entity);
        return bannerId == null ? null : banners.get(bannerId);
    }

    private boolean isValidPresetSlot(CraftingBannerData data, int slot) {
        return slot >= 0 && slot < getDisplaySlotCount(data);
    }

    private boolean executeConfiguredSlotAction(Player player, CraftingBannerData data, int slot, boolean rightClick) {
        DisplaySlotAction action = DisplaySlotAction.fromId(data.slotActions().get(slot));
        if (action == null || action == DisplaySlotAction.NONE) {
            return false;
        }
        switch (action) {
            case OPEN_MENU -> openCraftingMenu(player, data.id());
            case CRAFT_ONE -> craftConfiguredRecipe(player, data, false);
            case CRAFT_MAX -> craftConfiguredRecipe(player, data, true);
            case AMOUNT_DOWN -> adjustAmount(data, rightClick ? -8 : -1);
            case AMOUNT_UP -> adjustAmount(data, rightClick ? 8 : 1);
            case NEXT_PAGE -> {
                if (data.preset() == DisplayPreset.CHEST_3X9 || data.preset() == DisplayPreset.DOUBLE_CHEST_6X9 || data.preset() == DisplayPreset.CUSTOM) {
                    openGenericGridMenu(player, data, 1);
                }
            }
            case PREVIOUS_PAGE -> {
                if (data.preset() == DisplayPreset.CHEST_3X9 || data.preset() == DisplayPreset.DOUBLE_CHEST_6X9 || data.preset() == DisplayPreset.CUSTOM) {
                    openGenericGridMenu(player, data, 0);
                }
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    private DisplayLayout resolveLayout(CraftingBannerData data) {
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

    private int getDisplaySlotCount(CraftingBannerData data) {
        return resolveLayout(data).slotCount();
    }

    private String presetName(CraftingBannerData data) {
        if (data.preset() == DisplayPreset.CUSTOM) {
            return data.customColumns() + "x" + data.customRows();
        }
        return data.preset().id();
    }

    private String defaultTitle(CraftingBannerData data) {
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            return "Ofen";
        }
        if (data.preset() == DisplayPreset.CRAFTING_3X3) {
            return "Werkbank";
        }
        return "DisplayGUI " + presetName(data);
    }

    private ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }

    private boolean handleFurnaceDisplayInteraction(Player player, CraftingBannerData data, int slot, boolean rightClick) {
        Furnace furnace = resolveFurnace(data);
        if (furnace == null) {
            return false;
        }
        FurnaceInventory inventory = furnace.getInventory();
        if (slot == 0) {
            if (isEmptyMainHand(player)) {
                return takeFurnaceStoredItem(player, inventory, true, rightClick);
            }
            return insertIntoFurnaceSlot(player, inventory, true, rightClick);
        }
        if (slot == 4) {
            return insertIntoFurnaceSlot(player, inventory, false, rightClick);
        }
        if (slot == 3) {
            return takeFurnaceResult(player, inventory, rightClick);
        }
        if (slot == 5) {
            return collectStoredFurnaceExperience(player, furnace);
        }
        return false;
    }

    private boolean insertIntoFurnaceSlot(Player player, FurnaceInventory inventory, boolean inputSlot, boolean fullStack) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            return false;
        }
        if (inputSlot) {
            if (recipeMatcher.adaptCookingRecipe(hand, DisplayPreset.FURNACE_1X5, inventory.getHolder() instanceof Furnace furnace ? furnace.getBlock().getType() : Material.FURNACE) == null) {
                return false;
            }
        } else if (!hand.getType().isFuel()) {
            return false;
        }

        ItemStack target = inputSlot ? inventory.getSmelting() : inventory.getFuel();
        if (target != null && target.getType() != Material.AIR && target.getType() != hand.getType()) {
            return false;
        }

        int transferAmount = fullStack ? hand.getAmount() : 1;
        int currentAmount = target == null || target.getType() == Material.AIR ? 0 : target.getAmount();
        int maxStack = hand.getMaxStackSize();
        int accepted = Math.max(0, Math.min(transferAmount, maxStack - currentAmount));
        if (accepted <= 0) {
            return false;
        }

        ItemStack updated = hand.clone();
        updated.setAmount(currentAmount + accepted);
        if (inputSlot) {
            inventory.setSmelting(updated);
        } else {
            inventory.setFuel(updated);
        }

        hand.setAmount(hand.getAmount() - accepted);
        if (hand.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            player.getInventory().setItemInMainHand(hand);
        }
        return true;
    }

    private boolean takeFurnaceStoredItem(Player player, FurnaceInventory inventory, boolean inputSlot, boolean fullStack) {
        ItemStack stored = inputSlot ? inventory.getSmelting() : inventory.getFuel();
        if (stored == null || stored.getType() == Material.AIR) {
            return false;
        }

        ItemStack taken = stored.clone();
        if (!fullStack) {
            taken.setAmount(1);
        }

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(taken);
        int inserted = taken.getAmount() - overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (inserted <= 0) {
            return false;
        }

        stored.setAmount(stored.getAmount() - inserted);
        if (inputSlot) {
            inventory.setSmelting(stored.getAmount() <= 0 ? null : stored);
        } else {
            inventory.setFuel(stored.getAmount() <= 0 ? null : stored);
        }
        return true;
    }

    private boolean takeFurnaceResult(Player player, FurnaceInventory inventory, boolean fullStack) {
        ItemStack result = inventory.getResult();
        if (result == null || result.getType() == Material.AIR) {
            return false;
        }
        ItemStack taken = result.clone();
        if (!fullStack) {
            taken.setAmount(1);
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(taken);
        int inserted = taken.getAmount() - overflow.values().stream().mapToInt(ItemStack::getAmount).sum();
        if (inserted <= 0) {
            return false;
        }
        result.setAmount(result.getAmount() - inserted);
        inventory.setResult(result.getAmount() <= 0 ? null : result);
        return true;
    }

    private boolean collectStoredFurnaceExperience(Player player, Furnace furnace) {
        int experience = computeStoredFurnaceExperienceToAward(furnace);
        if (experience <= 0) {
            return false;
        }
        dropExperienceOrbs(player, furnace, experience);
        clearStoredFurnaceExperience(furnace);
        return true;
    }

    private int computeStoredFurnaceExperienceToAward(Furnace furnace) {
        try {
            Method method = furnace.getClass().getMethod("getRecipesUsed");
            Object value = method.invoke(furnace);
            if (!(value instanceof Map<?, ?> recipesUsed) || recipesUsed.isEmpty()) {
                return 0;
            }
            int total = 0;
            for (Map.Entry<?, ?> entry : recipesUsed.entrySet()) {
                if (!(entry.getKey() instanceof CookingRecipe<?> recipe) || !(entry.getValue() instanceof Number count)) {
                    continue;
                }
                total += calculateAwardedExperience(recipe.getExperience(), count.intValue());
            }
            return total;
        } catch (ReflectiveOperationException ignored) {
            return 0;
        }
    }

    private boolean isEmptyMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return hand == null || hand.getType() == Material.AIR;
    }

    private void dropExperienceOrbs(Player player, Furnace furnace, int experience) {
        World world = furnace.getWorld();
        var location = furnace.getBlock().getLocation().add(0.5, 1.0, 0.5);
        int remaining = experience;
        while (remaining > 0) {
            int orbValue = splitOrbValue(remaining);
            remaining -= orbValue;
            ExperienceOrb orb = world.spawn(location, ExperienceOrb.class);
            orb.setExperience(orbValue);
        }
        world.playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
    }

    private int splitOrbValue(int remaining) {
        if (remaining >= 2477) return 2477;
        if (remaining >= 1237) return 1237;
        if (remaining >= 617) return 617;
        if (remaining >= 307) return 307;
        if (remaining >= 149) return 149;
        if (remaining >= 73) return 73;
        if (remaining >= 37) return 37;
        if (remaining >= 17) return 17;
        if (remaining >= 7) return 7;
        if (remaining >= 3) return 3;
        return 1;
    }

    private int calculateAwardedExperience(float experiencePerRecipe, int recipeCount) {
        if (experiencePerRecipe <= 0.0f || recipeCount <= 0) {
            return 0;
        }
        float total = experiencePerRecipe * recipeCount;
        int floor = (int) Math.floor(total);
        float fraction = total - floor;
        if (fraction > 0.0f && Math.random() < fraction) {
            floor++;
        }
        return floor;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void clearStoredFurnaceExperience(Furnace furnace) {
        try {
            try {
                Method setter = furnace.getClass().getMethod("setRecipesUsed", Map.class);
                setter.invoke(furnace, new HashMap<>());
            } catch (NoSuchMethodException ignored) {
                Method method = furnace.getClass().getMethod("getRecipesUsed");
                Object value = method.invoke(furnace);
                if (value instanceof Map recipesUsed) {
                    try {
                        recipesUsed.clear();
                    } catch (UnsupportedOperationException ignoredToo) {
                        return;
                    }
                }
            }
            if (furnace instanceof TileState tileState) {
                tileState.update();
            } else {
                furnace.update();
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private int craftingResultSlot(CraftingBannerData data) {
        return switch (data.resultPosition()) {
            case LEFT -> 5;
            case TOP -> 1;
            case BOTTOM -> 13;
            case RIGHT -> 9;
        };
    }

    private void setRenderMode(CraftingBannerData data, DisplayRenderMode mode) {
        data.setRenderMode(mode);
        save();
        refreshDisplay(data);
    }

    private void setResultPosition(CraftingBannerData data, DisplayResultPosition position) {
        data.setResultPosition(position == null ? DisplayResultPosition.RIGHT : position);
        save();
        refreshDisplay(data);
    }

    private int clampColumns(int columns) {
        return Math.max(1, Math.min(9, columns));
    }

    private int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private boolean destroyStandAndDrop(UUID bannerId, Player player) {
        CraftingBannerData data = banners.remove(bannerId);
        if (data == null) {
            return false;
        }
        displayEntityManager.unregister(bannerId);
        Block block = resolveBlock(data);
        if (block != null && block.getType() != Material.AIR) {
            block.setType(Material.AIR, false);
        }
        ItemStack bannerItem = data.bannerItem();
        if (bannerItem == null) {
            bannerItem = new ItemStack(Material.WHITE_BANNER);
        }
        World world = Bukkit.getWorld(data.world());
        if (world != null) {
            world.dropItemNaturally(block != null ? block.getLocation().add(0.5, 0.5, 0.5) : player.getLocation(), bannerItem);
        }
        save();
        if (player != null) {
            player.sendMessage(Component.text("Display-Gestell entfernt und Banner gedroppt.", NamedTextColor.YELLOW));
        }
        return true;
    }
}
