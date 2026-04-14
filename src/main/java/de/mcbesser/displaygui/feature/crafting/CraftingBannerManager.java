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
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
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
    private static final int[] FURNACE_SLOTS = {1, 3, 5, 7, 8};
    private static final int GENERIC_PREV_SLOT = 45;
    private static final int GENERIC_PAGE_SLOT = 49;
    private static final int GENERIC_NEXT_SLOT = 53;

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
                data.setCraftAmount(clampAmount(entry.getInt("craft-amount", 1)));
                data.setTitle(entry.getString("title", "DisplayGUI"));
                DisplayRenderMode renderMode = DisplayRenderMode.fromId(entry.getString("render-mode", DisplayRenderMode.RECIPE_ONLY.id()));
                data.setRenderMode(renderMode == null ? DisplayRenderMode.RECIPE_ONLY : renderMode);
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
            config.set(path + ".preset", data.preset().id());
            config.set(path + ".custom-columns", data.customColumns());
            config.set(path + ".custom-rows", data.customRows());
            config.set(path + ".craft-amount", data.craftAmount());
            config.set(path + ".title", data.title());
            config.set(path + ".render-mode", data.renderMode().id());
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
        UUID bannerId = getOrCreateBannerId(block);
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
        } else if (below == Material.FURNACE || below == Material.BLAST_FURNACE || below == Material.SMOKER) {
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
            if (slot == 9) {
                craftConfiguredRecipe(player, data, rightClick);
                return true;
            }
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            if (slot == 3) {
                craftConfiguredRecipe(player, data, rightClick);
                return true;
            }
            if (slot == 4) {
                adjustAmount(data, rightClick ? 8 : 1);
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
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        int[] centeredMatrixSlots = {12, 13, 14, 21, 22, 23, 30, 31, 32};
        for (int i = 0; i < centeredMatrixSlots.length; i++) {
            inventory.setItem(centeredMatrixSlots[i], recipeMenuItem(data.matrix().get(i)));
        }

        inventory.setItem(4, named(Material.WRITABLE_BOOK, "Titel bearbeiten"));
        inventory.setItem(39, named(Material.RED_STAINED_GLASS_PANE, "-1"));
        inventory.setItem(40, named(Material.SLIME_BALL, "Menge: " + data.craftAmount()));
        inventory.setItem(41, named(Material.LIME_STAINED_GLASS_PANE, "+1"));
        inventory.setItem(17, modeItem(Material.PAPER, "Anzeige Rezept", data.renderMode() == DisplayRenderMode.RECIPE_ONLY));
        inventory.setItem(26, modeItem(Material.CRAFTING_TABLE, "Anzeige Rezept + Ergebnis", data.renderMode() == DisplayRenderMode.RECIPE_AND_RESULT));
        inventory.setItem(35, modeItem(Material.CHEST, "Anzeige Ergebnis", data.renderMode() == DisplayRenderMode.RESULT_ONLY));
        inventory.setItem(44, modeItem(Material.HONEY_BOTTLE, "Anzeige Ergebnis + Menge", data.renderMode() == DisplayRenderMode.RESULT_WITH_AMOUNT));

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
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        int start = currentPage * editablePerPage;
        int end = Math.min(displaySlots, start + editablePerPage);
        for (int i = start; i < end; i++) {
            inventory.setItem(i - start, itemFromName(data.matrix().get(i)));
        }
        inventory.setItem(GENERIC_PREV_SLOT, named(Material.ARROW, "Seite zurueck"));
        inventory.setItem(GENERIC_PAGE_SLOT, named(Material.BOOK, "Seite " + (currentPage + 1) + "/" + (maxPage + 1)));
        inventory.setItem(GENERIC_NEXT_SLOT, named(Material.ARROW, "Seite vor"));

        player.openInventory(inventory);
    }

    private void openFurnaceMenu(Player player, CraftingBannerData data) {
        CraftingMenuHolder holder = new CraftingMenuHolder(data.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 18, Component.text("DisplayGUI 1x5"));
        holder.setInventory(inventory);
        ItemStack filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        for (int i = 0; i < FURNACE_SLOTS.length; i++) {
            inventory.setItem(FURNACE_SLOTS[i], itemFromName(data.matrix().get(i)));
        }

        inventory.setItem(DECREASE_SLOT - 27, named(Material.RED_STAINED_GLASS_PANE, "-1"));
        inventory.setItem(INFO_SLOT - 27, named(Material.SLIME_BALL, "Menge: " + data.craftAmount()));
        inventory.setItem(INCREASE_SLOT - 27, named(Material.LIME_STAINED_GLASS_PANE, "+1"));

        RecipeMatch match = findRecipeMatch(data);
        if (match != null) {
            ItemStack result = match.result().clone();
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("Ergebnis", NamedTextColor.GREEN));
                result.setItemMeta(meta);
            }
            inventory.setItem(6, result);
        }

        player.openInventory(inventory);
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

        if (data.preset() == DisplayPreset.CRAFTING_3X3 && rawSlot == 4) {
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
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            handleFurnaceMenuClick(player, data, rawSlot, click);
            return;
        }

        if (rawSlot == 39) {
            adjustAmount(data, click.isRightClick() ? -8 : -1);
            openCraftingMenu(player, data.id());
            return;
        }
        if (rawSlot == 41) {
            adjustAmount(data, click.isRightClick() ? 8 : 1);
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
            openCraftingMenu(player, data.id());
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
        openGenericGridMenu(player, data, page);
    }

    private void handleFurnaceMenuClick(Player player, CraftingBannerData data, int rawSlot, ClickType click) {
        if (rawSlot == 14) {
            adjustAmount(data, click.isRightClick() ? -8 : -1);
            openFurnaceMenu(player, data);
            return;
        }
        if (rawSlot == 16) {
            adjustAmount(data, click.isRightClick() ? 8 : 1);
            openFurnaceMenu(player, data);
            return;
        }
        if (rawSlot == 6) {
            craftConfiguredRecipe(player, data, click.isRightClick());
            openFurnaceMenu(player, data);
            return;
        }

        for (int i = 0; i < FURNACE_SLOTS.length; i++) {
            if (rawSlot != FURNACE_SLOTS[i]) {
                continue;
            }
            ItemStack cursor = player.getItemOnCursor();
            data.matrix().set(i, cursor == null || cursor.getType() == Material.AIR ? "" : cursor.getType().name());
            save();
            refreshDisplay(data);
            openFurnaceMenu(player, data);
            return;
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
            actor.sendMessage(Component.text("Beschriftung fuer Slot " + (slot + 1) + " gesetzt.", NamedTextColor.GREEN));
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
            actor.sendMessage(Component.text("Beschriftung fuer Slot " + (slot + 1) + " gesetzt.", NamedTextColor.GREEN));
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
            actor.sendMessage(Component.text("Klickaktion fuer Slot " + (slot + 1) + " ist jetzt " + data.slotActions().get(slot), NamedTextColor.GREEN));
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
            actor.sendMessage(Component.text("Klickaktion fuer Slot " + (slot + 1) + " ist jetzt " + data.slotActions().get(slot), NamedTextColor.GREEN));
        }
        return true;
    }

    private void craftConfiguredRecipe(Player player, CraftingBannerData data, boolean maxMode) {
        RecipeMatch match = findRecipeMatch(data);
        if (match == null) {
            player.sendMessage(Component.text("Kein gueltiges Rezept vorhanden.", NamedTextColor.RED));
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
        RecipeMatch match = findRecipeMatch(data);
        displayEntityManager.register(new CraftingBannerDisplay(data, new DisplayAnchor(block.getLocation(), data.yaw()), match));
    }

    private RecipeMatch findRecipeMatch(CraftingBannerData data) {
        ItemStack[] matrix = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            matrix[i] = itemFromName(data.matrix().get(i));
        }
        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            return recipeMatcher.adaptCookingRecipe(matrix[0], data.preset());
        }
        return recipeMatcher.findMatchingCraftingRecipe(matrix);
    }

    private Block resolveBlock(CraftingBannerData data) {
        World world = Bukkit.getWorld(data.world());
        return world == null ? null : world.getBlockAt(data.x(), data.y(), data.z());
    }

    private UUID getOrCreateBannerId(Block block) {
        UUID existing = getBannerId(block);
        if (existing != null) {
            return existing;
        }
        UUID created = UUID.randomUUID();
        BlockState state = block.getState();
        if (state instanceof Banner banner) {
            banner.getPersistentDataContainer().set(bannerIdKey, PersistentDataType.STRING, created.toString());
            banner.update(true, false);
        }
        return created;
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

        if (data.preset() == DisplayPreset.FURNACE_1X5) {
            if (data.matrix().get(1).isBlank()) {
                data.matrix().set(1, Material.COAL.name());
            }
            if (data.matrix().get(2).isBlank()) {
                data.matrix().set(2, Material.BLAZE_POWDER.name());
            }
        }
        if (data.preset() == DisplayPreset.CUSTOM && (data.title() == null || data.title().isBlank() || data.title().equals("DisplayGUI"))) {
            data.setTitle(defaultTitle(data));
        }
    }

    private ItemStack named(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.YELLOW));
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
        return "DisplayGUI " + presetName(data);
    }

    private void setRenderMode(CraftingBannerData data, DisplayRenderMode mode) {
        data.setRenderMode(mode);
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
