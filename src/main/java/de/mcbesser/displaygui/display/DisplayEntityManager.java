package de.mcbesser.displaygui.display;

import de.mcbesser.displaygui.DisplayGUIPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.util.RayTraceResult;

public final class DisplayEntityManager {
    private static final double DEFAULT_DETAIL_VIEW_DISTANCE = 8.0D;
    private static final double DEFAULT_MAX_VIEW_DISTANCE = 64.0D;
    private static final double PREVIEW_TITLE_OFFSET_Y = 0.42D;
    private static final double SPAWN_ANIMATION_START_Y = 0.12D;
    private static final int SPAWN_ANIMATION_TICKS = 6;
    private static final int POLE_ANIMATION_TICKS = 3;
    private static final int POLE_ANIMATION_DELAY_TICKS = 1;
    private static final int TITLE_SPAWN_ANIMATION_TICKS = 6;
    private static final int TITLE_DESPAWN_ANIMATION_TICKS = 14;
    private static final int SPAWN_ANIMATION_DELAY_TICKS = 2;

    private final DisplayGUIPlugin plugin;
    private final NamespacedKey displayIdKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey kindKey;
    private final Map<UUID, DisplayRenderable> renderables = new HashMap<>();
    private final Map<UUID, Cluster> clusters = new HashMap<>();
    private final Set<UUID> closingClusters = new HashSet<>();
    private BukkitTask task;
    private int refreshCursor;

    public DisplayEntityManager(DisplayGUIPlugin plugin) {
        this.plugin = plugin;
        this.displayIdKey = new NamespacedKey(plugin, "display-id");
        this.slotKey = new NamespacedKey(plugin, "display-slot");
        this.kindKey = new NamespacedKey(plugin, "display-kind");
    }

    public void start() {
        cleanupManagedEntities();
        refreshCursor = 0;
        long refreshTicks = Math.max(2L, plugin.getConfig().getLong("display.refresh-ticks", 10L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 3L, refreshTicks);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (Cluster cluster : clusters.values()) {
            cluster.remove();
        }
        clusters.clear();
        renderables.clear();
        refreshCursor = 0;
    }

    public void register(DisplayRenderable renderable) {
        renderables.put(renderable.uniqueId(), renderable);
        refresh(renderable.uniqueId());
    }

    public void unregister(UUID displayId) {
        renderables.remove(displayId);
        Cluster cluster = clusters.remove(displayId);
        if (cluster != null) {
            cluster.remove();
        }
    }

    public boolean isManagedEntity(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(displayIdKey, PersistentDataType.STRING);
    }

    public UUID getDisplayId(Entity entity) {
        if (entity == null) {
            return null;
        }
        String raw = entity.getPersistentDataContainer().get(displayIdKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public int getSlot(Entity entity) {
        Integer slot = entity.getPersistentDataContainer().get(slotKey, PersistentDataType.INTEGER);
        return slot == null ? -1 : slot;
    }

    public boolean isStandEntity(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(displayIdKey, PersistentDataType.STRING)
                && "stand".equals(entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING));
    }

    public HoveredDisplayInfo getHoveredDisplayInfo(org.bukkit.entity.Player player) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        RayTraceResult rayTrace = player.getWorld().rayTrace(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                getSidebarInteractionRange(player),
                FluidCollisionMode.NEVER,
                true,
                0.0,
                this::isSidebarTarget
        );
        if (rayTrace == null || rayTrace.getHitEntity() == null) {
            return null;
        }
        Entity entity = rayTrace.getHitEntity();
        UUID displayId = getDisplayId(entity);
        int slotIndex = getSlot(entity);
        DisplayRenderable renderable = displayId == null ? null : renderables.get(displayId);
        if (renderable == null || !renderable.supportsSidebar() || slotIndex < 0) {
            return null;
        }
        DisplayContent.DisplaySlot slot = findSlot(renderable.content(), slotIndex);
        if (slot == null) {
            return null;
        }
        ItemStack icon = slot.icon() == null ? new ItemStack(Material.AIR) : slot.icon().clone();
        ItemMeta meta = icon.getItemMeta();
        Component title;
        if (meta != null && meta.displayName() != null) {
            title = meta.displayName();
        } else if (meta != null && meta.hasItemName()) {
            title = meta.itemName();
        } else if (icon.getType() != Material.AIR) {
            title = Component.translatable(icon.getType().translationKey());
        } else {
            title = Component.text("Slot");
        }
        List<Component> lore = new ArrayList<>();
        if (meta != null && meta.lore() != null) {
            for (Component line : meta.lore()) {
                if (line == null) {
                    continue;
                }
                String plain = PlainTextComponentSerializer.plainText().serialize(line).trim();
                if (!plain.isEmpty()) {
                    lore.add(normalizeLoreLine(line));
                }
            }
        }
        if (slot.label() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(slot.label()).trim();
            if (!plain.isEmpty()) {
                lore.add(normalizeLoreLine(slot.label()));
            }
        }
        if (slot.amountText() != null) {
            String plain = PlainTextComponentSerializer.plainText().serialize(slot.amountText()).trim();
            if (!plain.isEmpty()) {
                lore.add(normalizeLoreLine(slot.amountText()));
            }
        }
        return new HoveredDisplayInfo(displayId, slotIndex, title, lore);
    }

    private boolean isSidebarTarget(Entity entity) {
        return entity instanceof Interaction
                && isManagedEntity(entity)
                && !isStandEntity(entity)
                && "slot".equals(entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING))
                && getSlot(entity) >= 0;
    }

    private double getSidebarInteractionRange(org.bukkit.entity.Player player) {
        return switch (player.getGameMode()) {
            case CREATIVE -> 5.0;
            default -> 4.5;
        };
    }

    public void refresh(UUID displayId) {
        DisplayRenderable renderable = renderables.get(displayId);
        if (renderable == null || !renderable.isActive()) {
            unregister(displayId);
            return;
        }

        Location anchorLocation = renderable.anchor().location();
        ViewerState viewerState = getViewerState(anchorLocation);
        if (viewerState == ViewerState.NONE) {
            Cluster existingCluster = clusters.get(displayId);
            if (existingCluster != null) {
                closeCluster(displayId, existingCluster, renderable.anchor().yaw());
            }
            return;
        }

        boolean detailedVisible = viewerState == ViewerState.DETAIL;
        Cluster cluster = clusters.get(displayId);
        if (closingClusters.contains(displayId)) {
            Cluster closingCluster = clusters.remove(displayId);
            if (closingCluster != null) {
                closingCluster.remove();
            }
            closingClusters.remove(displayId);
            cluster = null;
        }
        if (cluster == null || !cluster.isValid() || !cluster.layoutKey().equals(renderable.layout().key())) {
            if (cluster != null) {
                cluster.remove();
            }
            cluster = spawn(renderable, detailedVisible);
            clusters.put(displayId, cluster);
        } else if (cluster.detailed() && !detailedVisible) {
            collapseToPreview(cluster, renderable);
        } else if (!cluster.detailed() && detailedVisible) {
            cluster.remove();
            cluster = spawn(renderable, true);
            clusters.put(displayId, cluster);
        }

        DisplayContent content = renderable.content();
        cluster.title.text(content.title());
        if (!cluster.consumeFreshSpawn()) {
            cluster.title.teleport(transform(
                    renderable.anchor().location(),
                    0.0,
                    viewerState == ViewerState.DETAIL ? renderable.layout().titleOffsetY() : PREVIEW_TITLE_OFFSET_Y,
                    0.0,
                    renderable.anchor().yaw()
            ));
        }
        cluster.title.setRotation(renderable.anchor().yaw(), 0.0f);

        DisplayLayout layout = renderable.layout();
        if (!detailedVisible) {
            return;
        }

        for (int i = 0; i < layout.slotCount(); i++) {
            DisplayContent.DisplaySlot slot = findSlot(content, i);
            ItemDisplay background = cluster.backgrounds.get(i);

            background.setItemStack(slot != null && slot.background() != null
                    ? slot.background().clone()
                    : new ItemStack(Material.AIR));
            background.setRotation(renderable.anchor().yaw(), 0.0f);

            if (slot == null || isEmptyItem(slot.icon())) {
                cluster.removeIcon(i);
                cluster.removeAmount(i);
                cluster.removeInteraction(i);
                continue;
            }

            ItemDisplay icon = ensureIcon(cluster, i, renderable, slot);
            icon.setItemStack(slot.icon());
            icon.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(slot.scale(), slot.scale(), slot.scale()),
                    new Quaternionf()
            ));
            icon.setRotation(renderable.anchor().yaw(), 0.0f);

            if (isEmptyComponent(slot.amountText())) {
                cluster.removeAmount(i);
            } else {
                TextDisplay amount = ensureAmount(cluster, i, renderable);
                amount.text(slot.amountText());
                amount.setRotation(renderable.anchor().yaw(), 0.0f);
            }

            Interaction interaction = ensureInteraction(cluster, i, renderable);
            interaction.setInteractionHeight(slot.interactionHeight());
            interaction.setInteractionWidth(slot.interactionWidth());
        }
    }

    private void refreshAll() {
        if (renderables.isEmpty()) {
            refreshCursor = 0;
            return;
        }
        List<UUID> ids = new ArrayList<>(renderables.keySet());
        int budget = Math.max(1, plugin.getConfig().getInt("display.refresh-budget-per-run", 4));
        int processed = 0;
        while (processed < budget && processed < ids.size()) {
            if (refreshCursor >= ids.size()) {
                refreshCursor = 0;
            }
            refresh(ids.get(refreshCursor));
            refreshCursor++;
            processed++;
        }
    }

    private Cluster spawn(DisplayRenderable renderable, boolean detailed) {
        removeEntitiesForDisplay(renderable.uniqueId());
        DisplayAnchor anchor = renderable.anchor();
        DisplayLayout layout = renderable.layout();
        Location titleLocation = transform(anchor.location(), 0.0, detailed ? layout.titleOffsetY() : PREVIEW_TITLE_OFFSET_Y, 0.0, anchor.yaw());
        Location titleStartLocation = spawnAnimationStart(anchor.location(), titleLocation, layout);

        TextDisplay title = anchor.location().getWorld().spawn(titleStartLocation, TextDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "title");
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        animateSpawn(title, titleLocation, TITLE_SPAWN_ANIMATION_TICKS);

        List<BlockDisplay> standDisplays = spawnStand(renderable, detailed);
        List<Interaction> standInteractions = spawnStandInteractions(renderable);
        List<ItemDisplay> backgrounds = new ArrayList<>(layout.slotCount());
        List<ItemDisplay> icons = new ArrayList<>(layout.slotCount());
        List<TextDisplay> amounts = new ArrayList<>(layout.slotCount());
        List<Interaction> interactions = new ArrayList<>(layout.slotCount());

        if (!detailed) {
            return new Cluster(anchor.location().clone(), layout, false, title, standDisplays, standInteractions, backgrounds, icons, amounts, interactions);
        }

        for (int slot = 0; slot < layout.slotCount(); slot++) {
            int row = slot / layout.columns();
            int column = slot % layout.columns();
            double x = (-((layout.columns() - 1) * layout.slotSpacing()) / 2.0) + (column * layout.slotSpacing());
            double y = layout.originY() - (row * layout.slotSpacing());

            Location backgroundLocation = transform(anchor.location(), x, y, 0.0, anchor.yaw());
            Location backgroundStartLocation = spawnAnimationStart(anchor.location(), backgroundLocation, layout);
            ItemDisplay background = anchor.location().getWorld().spawn(backgroundStartLocation, ItemDisplay.class, entity -> {
                prepare(entity, renderable.uniqueId(), -1, "background");
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(layout.backgroundScale(), layout.backgroundScale(), layout.backgroundScale()),
                        new Quaternionf()
                ));
                entity.setRotation(anchor.yaw(), 0.0f);
            });
            animateSpawn(background, backgroundLocation, SPAWN_ANIMATION_TICKS);
            backgrounds.add(background);
            icons.add(null);
            amounts.add(null);
            interactions.add(null);
        }

        return new Cluster(anchor.location().clone(), layout, true, title, standDisplays, standInteractions, backgrounds, icons, amounts, interactions);
    }

    private DisplayContent.DisplaySlot findSlot(DisplayContent content, int index) {
        for (DisplayContent.DisplaySlot slot : content.slots()) {
            if (slot.index() == index) {
                return slot;
            }
        }
        return null;
    }

    private boolean isEmptyItem(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR;
    }

    private boolean isEmptyComponent(Component component) {
        return component == null || PlainTextComponentSerializer.plainText().serialize(component).trim().isEmpty();
    }

    private SlotPosition slotPosition(DisplayRenderable renderable, int slot) {
        return slotPosition(renderable.layout(), slot);
    }

    private SlotPosition slotPosition(DisplayLayout layout, int slot) {
        int row = slot / layout.columns();
        int column = slot % layout.columns();
        double x = (-((layout.columns() - 1) * layout.slotSpacing()) / 2.0) + (column * layout.slotSpacing());
        double y = layout.originY() - (row * layout.slotSpacing());
        return new SlotPosition(x, y);
    }

    private ItemDisplay ensureIcon(Cluster cluster, int slot, DisplayRenderable renderable, DisplayContent.DisplaySlot displaySlot) {
        ItemDisplay existing = cluster.icons.get(slot);
        if (existing != null && existing.isValid()) {
            return existing;
        }
        DisplayAnchor anchor = renderable.anchor();
        SlotPosition position = slotPosition(renderable, slot);
        Location iconLocation = transform(anchor.location(), position.x(), position.y() + 0.06, 0.02, anchor.yaw());
        Location iconStartLocation = spawnAnimationStart(anchor.location(), iconLocation, renderable.layout());
        ItemDisplay created = anchor.location().getWorld().spawn(iconStartLocation, ItemDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "icon");
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(displaySlot.scale(), displaySlot.scale(), displaySlot.scale()),
                    new Quaternionf()
            ));
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        animateSpawn(created, iconLocation, SPAWN_ANIMATION_TICKS);
        cluster.icons.set(slot, created);
        return created;
    }

    private TextDisplay ensureAmount(Cluster cluster, int slot, DisplayRenderable renderable) {
        TextDisplay existing = cluster.amounts.get(slot);
        if (existing != null && existing.isValid()) {
            return existing;
        }
        DisplayAnchor anchor = renderable.anchor();
        DisplayLayout layout = renderable.layout();
        SlotPosition position = slotPosition(renderable, slot);
        Location amountLocation = transform(anchor.location(), position.x(), position.y() - 0.22, 0.02, anchor.yaw());
        Location amountStartLocation = spawnAnimationStart(anchor.location(), amountLocation, renderable.layout());
        TextDisplay created = anchor.location().getWorld().spawn(amountStartLocation, TextDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "amount");
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(layout.textScale(), layout.textScale(), layout.textScale()),
                    new Quaternionf()
            ));
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        animateSpawn(created, amountLocation, SPAWN_ANIMATION_TICKS);
        cluster.amounts.set(slot, created);
        return created;
    }

    private Interaction ensureInteraction(Cluster cluster, int slot, DisplayRenderable renderable) {
        Interaction existing = cluster.interactions.get(slot);
        if (existing != null && existing.isValid()) {
            return existing;
        }
        DisplayAnchor anchor = renderable.anchor();
        SlotPosition position = slotPosition(renderable, slot);
        Location interactionLocation = transform(anchor.location(), position.x(), position.y() - 0.255, 0.0, anchor.yaw());
        Interaction created = anchor.location().getWorld().spawn(interactionLocation, Interaction.class, entity -> {
            prepare(entity, renderable.uniqueId(), slot, "slot");
            entity.setResponsive(true);
            entity.setInteractionHeight(0.50f);
            entity.setInteractionWidth(0.50f);
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        cluster.interactions.set(slot, created);
        return created;
    }

    private List<BlockDisplay> spawnStand(DisplayRenderable renderable, boolean detailed) {
        DisplayAnchor anchor = renderable.anchor();
        DisplayLayout layout = renderable.layout();
        List<BlockDisplay> stands = new ArrayList<>(2);
        float poleHeight = 1.0625f + Math.max(0, layout.rows() - 1) * 0.5f;

        if (detailed) {
            Location poleLocation = transform(anchor.location(), 0.0, 1.02, -0.04, anchor.yaw());
            BlockDisplay pole = anchor.location().getWorld().spawn(poleLocation, BlockDisplay.class, entity -> {
                prepare(entity, renderable.uniqueId(), -1, "stand");
                entity.setBlock(org.bukkit.Material.DARK_OAK_FENCE.createBlockData());
                entity.setTransformation(poleTransformation(0.01f));
                entity.setRotation(anchor.yaw(), 0.0f);
            });
            animateTransformation(pole, poleTransformation(poleHeight), POLE_ANIMATION_TICKS, POLE_ANIMATION_DELAY_TICKS);
            stands.add(pole);
        }

        Location baseLocation = transform(anchor.location(), 0.0, 0.0, 0.0, anchor.yaw());
        BlockDisplay base = anchor.location().getWorld().spawn(baseLocation, BlockDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "stand");
            entity.setBlock(org.bukkit.Material.DARK_OAK_PRESSURE_PLATE.createBlockData());
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.5f, 0.0f, -0.5f),
                    new Quaternionf(),
                    new Vector3f(1.0f, 1.0f, 1.0f),
                    new Quaternionf()
            ));
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        stands.add(base);

        return stands;
    }

    private List<Interaction> spawnStandInteractions(DisplayRenderable renderable) {
        DisplayAnchor anchor = renderable.anchor();
        List<Interaction> interactions = new ArrayList<>(1);
        Location baseHitboxLocation = transform(anchor.location(), 0.0, 0.02, 0.0, anchor.yaw());
        Interaction baseHitbox = anchor.location().getWorld().spawn(baseHitboxLocation, Interaction.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "stand");
            entity.setResponsive(true);
            entity.setInteractionHeight(0.16f);
            entity.setInteractionWidth(1.0f);
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        interactions.add(baseHitbox);

        return interactions;
    }

    private void prepare(Entity entity, UUID displayId, int slot, String kind) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(displayIdKey, PersistentDataType.STRING, displayId.toString());
        entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        if (slot >= 0) {
            entity.getPersistentDataContainer().set(slotKey, PersistentDataType.INTEGER, slot);
        }
    }

    private ViewerState getViewerState(Location location) {
        if (location == null || location.getWorld() == null) {
            return ViewerState.NONE;
        }
        double detailDistance = plugin.getConfig().getDouble("display.detail-view-distance", DEFAULT_DETAIL_VIEW_DISTANCE);
        double maxDistance = plugin.getConfig().getDouble("display.max-view-distance", DEFAULT_MAX_VIEW_DISTANCE);
        double detailDistanceSquared = detailDistance * detailDistance;
        double maxDistanceSquared = maxDistance * maxDistance;
        boolean hasPreviewViewer = false;
        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            if (player == null || !player.isOnline() || player.isDead() || player.getWorld() != location.getWorld()) {
                continue;
            }
            double distanceSquared = player.getLocation().distanceSquared(location);
            if (distanceSquared <= detailDistanceSquared) {
                return ViewerState.DETAIL;
            }
            if (distanceSquared <= maxDistanceSquared) {
                hasPreviewViewer = true;
            }
        }
        return hasPreviewViewer ? ViewerState.PREVIEW : ViewerState.NONE;
    }

    private void cleanupManagedEntities() {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isManagedEntity(entity)) {
                    entity.remove();
                }
            }
        }
    }

    private void removeEntitiesForDisplay(UUID displayId) {
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.isValid() || !isManagedEntity(entity)) {
                    continue;
                }
                UUID entityDisplayId = getDisplayId(entity);
                if (displayId.equals(entityDisplayId)) {
                    entity.remove();
                }
            }
        }
    }

    private Location transform(Location base, double localX, double localY, double localZ, float yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double worldX = (localX * cos) - (localZ * sin);
        double worldZ = (localX * sin) + (localZ * cos);
        Location transformed = base.clone().add(0.5 + worldX, localY, 0.5 + worldZ);
        transformed.setYaw(yaw);
        transformed.setPitch(0.0f);
        return transformed;
    }

    private Location spawnAnimationStart(Location anchor, Location target, DisplayLayout layout) {
        Location start = target.clone();
        start.setY(anchor.getY() + animationStartY(layout));
        return start;
    }

    private double animationStartY(DisplayLayout layout) {
        return layout.originY() - (Math.max(0, layout.rows() - 1) * layout.slotSpacing());
    }

    private void animateSpawn(Display display, Location target, int ticks) {
        display.setTeleportDuration(ticks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setTeleportDuration(ticks);
                display.teleport(target);
            }
        }, SPAWN_ANIMATION_DELAY_TICKS);
    }

    private Transformation poleTransformation(float height) {
        return new Transformation(
                new Vector3f(-0.11f, -1.0f, -0.11f),
                new Quaternionf(),
                new Vector3f(0.22f, Math.max(0.01f, height), 0.22f),
                new Quaternionf()
        );
    }

    private void animateTransformation(Display display, Transformation target, int ticks) {
        animateTransformation(display, target, ticks, SPAWN_ANIMATION_DELAY_TICKS);
    }

    private void animateTransformation(Display display, Transformation target, int ticks, int delayTicks) {
        display.setInterpolationDelay(delayTicks);
        display.setInterpolationDuration(ticks);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.setTransformation(target);
            }
        }, delayTicks);
    }

    private void collapseToPreview(Cluster cluster, DisplayRenderable renderable) {
        cluster.setDetailed(false);
        animateTitle(cluster, renderable, PREVIEW_TITLE_OFFSET_Y);
        animateDetailsDown(cluster, renderable);
        Bukkit.getScheduler().runTaskLater(plugin, cluster::removeDetailVisuals, SPAWN_ANIMATION_TICKS + 1L);
    }

    private void closeCluster(UUID displayId, Cluster cluster, float yaw) {
        if (!closingClusters.add(displayId)) {
            return;
        }
        Location base = cluster.blockLocation();
        animateDisplay(cluster.title(), transform(base, 0.0, animationStartY(cluster.layout()), 0.0, yaw), TITLE_DESPAWN_ANIMATION_TICKS);
        animateDetailsDown(cluster, base, yaw);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            clusters.remove(displayId);
            closingClusters.remove(displayId);
            cluster.remove();
        }, Math.max(SPAWN_ANIMATION_TICKS, TITLE_DESPAWN_ANIMATION_TICKS) + 1L);
    }

    private void animateTitle(Cluster cluster, DisplayRenderable renderable, double targetY) {
        animateDisplay(cluster.title(), transform(renderable.anchor().location(), 0.0, targetY, 0.0, renderable.anchor().yaw()), TITLE_DESPAWN_ANIMATION_TICKS);
    }

    private void animateDetailsDown(Cluster cluster, DisplayRenderable renderable) {
        animateDetailsDown(cluster, renderable.anchor().location(), renderable.anchor().yaw());
    }

    private void animateDetailsDown(Cluster cluster, Location base, float yaw) {
        if (cluster.standDisplays().size() > 1) {
            BlockDisplay pole = cluster.standDisplays().get(0);
            if (pole != null && pole.isValid()) {
                animateTransformation(pole, poleTransformation(0.01f), POLE_ANIMATION_TICKS, POLE_ANIMATION_DELAY_TICKS);
            }
        }
        double startY = animationStartY(cluster.layout());
        for (int i = 0; i < cluster.backgrounds().size(); i++) {
            SlotPosition position = slotPosition(cluster.layout(), i);
            Location target = transform(base, position.x(), startY, 0.0, yaw);
            animateDisplay(cluster.backgrounds().get(i), target, SPAWN_ANIMATION_TICKS);
        }
        for (int i = 0; i < cluster.icons().size(); i++) {
            SlotPosition position = slotPosition(cluster.layout(), i);
            animateDisplay(cluster.icons().get(i), transform(base, position.x(), startY + 0.06, 0.02, yaw), SPAWN_ANIMATION_TICKS);
        }
        for (int i = 0; i < cluster.amounts().size(); i++) {
            SlotPosition position = slotPosition(cluster.layout(), i);
            animateDisplay(cluster.amounts().get(i), transform(base, position.x(), startY - 0.22, 0.02, yaw), SPAWN_ANIMATION_TICKS);
        }
    }

    private void animateDisplay(Display display, Location target, int ticks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setTeleportDuration(ticks);
        display.teleport(target);
    }

    private Component normalizeLoreLine(Component line) {
        String plain = PlainTextComponentSerializer.plainText().serialize(line).trim();
        if (plain.isEmpty()) {
            return Component.empty();
        }
        int separator = plain.indexOf(':');
        if (separator < 0) {
            return Component.text(plain, NamedTextColor.WHITE);
        }
        String key = plain.substring(0, separator).trim();
        String value = plain.substring(separator + 1).trim();
        TextComponent.Builder builder = Component.text();
        builder.append(Component.text(key + ":", NamedTextColor.YELLOW));
        if (!value.isEmpty()) {
            builder.append(Component.text(" " + value, NamedTextColor.WHITE));
        }
        return builder.build();
    }

    private enum ViewerState {
        NONE,
        PREVIEW,
        DETAIL
    }

    private record SlotPosition(double x, double y) {
    }

    private static final class Cluster {
        private final Location blockLocation;
        private final DisplayLayout layout;
        private boolean detailed;
        private boolean freshSpawn = true;
        private final TextDisplay title;
        private final List<BlockDisplay> standDisplays;
        private final List<Interaction> standInteractions;
        private final List<ItemDisplay> backgrounds;
        private final List<ItemDisplay> icons;
        private final List<TextDisplay> amounts;
        private final List<Interaction> interactions;

        private Cluster(Location blockLocation, DisplayLayout layout, boolean detailed, TextDisplay title,
                        List<BlockDisplay> standDisplays, List<Interaction> standInteractions,
                        List<ItemDisplay> backgrounds, List<ItemDisplay> icons,
                        List<TextDisplay> amounts, List<Interaction> interactions) {
            this.blockLocation = blockLocation;
            this.layout = layout;
            this.detailed = detailed;
            this.title = title;
            this.standDisplays = standDisplays;
            this.standInteractions = standInteractions;
            this.backgrounds = backgrounds;
            this.icons = icons;
            this.amounts = amounts;
            this.interactions = interactions;
        }

        private String layoutKey() {
            return layout.key();
        }

        private Location blockLocation() {
            return blockLocation;
        }

        private DisplayLayout layout() {
            return layout;
        }

        private boolean detailed() {
            return detailed;
        }

        private void setDetailed(boolean detailed) {
            this.detailed = detailed;
        }

        private boolean consumeFreshSpawn() {
            boolean fresh = freshSpawn;
            freshSpawn = false;
            return fresh;
        }

        private TextDisplay title() {
            return title;
        }

        private List<BlockDisplay> standDisplays() {
            return standDisplays;
        }

        private List<ItemDisplay> backgrounds() {
            return backgrounds;
        }

        private List<ItemDisplay> icons() {
            return icons;
        }

        private List<TextDisplay> amounts() {
            return amounts;
        }

        private void removeIcon(int slot) {
            ItemDisplay display = icons.get(slot);
            if (display != null && display.isValid()) {
                display.remove();
            }
            icons.set(slot, null);
        }

        private void removeAmount(int slot) {
            TextDisplay display = amounts.get(slot);
            if (display != null && display.isValid()) {
                display.remove();
            }
            amounts.set(slot, null);
        }

        private void removeInteraction(int slot) {
            Interaction interaction = interactions.get(slot);
            if (interaction != null && interaction.isValid()) {
                interaction.remove();
            }
            interactions.set(slot, null);
        }

        private void removeDetailVisuals() {
            if (standDisplays.size() > 1) {
                BlockDisplay pole = standDisplays.remove(0);
                if (pole != null && pole.isValid()) {
                    pole.remove();
                }
            }
            for (ItemDisplay display : backgrounds) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (ItemDisplay display : icons) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (TextDisplay display : amounts) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction != null && interaction.isValid()) {
                    interaction.remove();
                }
            }
            backgrounds.clear();
            icons.clear();
            amounts.clear();
            interactions.clear();
        }

        private boolean isValid() {
            if (title == null || !title.isValid()) {
                return false;
            }
            for (BlockDisplay display : standDisplays) {
                if (display == null || !display.isValid()) {
                    return false;
                }
            }
            for (Interaction interaction : standInteractions) {
                if (interaction == null || !interaction.isValid()) {
                    return false;
                }
            }
            for (ItemDisplay display : backgrounds) {
                if (display == null || !display.isValid()) {
                    return false;
                }
            }
            for (ItemDisplay display : icons) {
                if (display != null && !display.isValid()) {
                    return false;
                }
            }
            for (TextDisplay display : amounts) {
                if (display != null && !display.isValid()) {
                    return false;
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction != null && !interaction.isValid()) {
                    return false;
                }
            }
            return true;
        }

        private void remove() {
            if (title != null && title.isValid()) {
                title.remove();
            }
            for (BlockDisplay display : standDisplays) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (Interaction interaction : standInteractions) {
                if (interaction != null && interaction.isValid()) {
                    interaction.remove();
                }
            }
            for (ItemDisplay display : backgrounds) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (ItemDisplay display : icons) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (TextDisplay display : amounts) {
                if (display != null && display.isValid()) {
                    display.remove();
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction != null && interaction.isValid()) {
                    interaction.remove();
                }
            }
        }
    }

    public record HoveredDisplayInfo(UUID displayId, int slot, Component title, List<Component> lore) {
    }
}
