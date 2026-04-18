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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.util.RayTraceResult;

public final class DisplayEntityManager {
    private final DisplayGUIPlugin plugin;
    private final NamespacedKey displayIdKey;
    private final NamespacedKey slotKey;
    private final NamespacedKey kindKey;
    private final Map<UUID, DisplayRenderable> renderables = new HashMap<>();
    private final Map<UUID, Cluster> clusters = new HashMap<>();
    private BukkitTask task;

    public DisplayEntityManager(DisplayGUIPlugin plugin) {
        this.plugin = plugin;
        this.displayIdKey = new NamespacedKey(plugin, "display-id");
        this.slotKey = new NamespacedKey(plugin, "display-slot");
        this.kindKey = new NamespacedKey(plugin, "display-kind");
    }

    public void start() {
        long refreshTicks = Math.max(2L, plugin.getConfig().getLong("display.refresh-ticks", 10L));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 1L, refreshTicks);
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
        if (renderable == null || slotIndex < 0) {
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

        Cluster cluster = clusters.get(displayId);
        if (cluster == null || !cluster.isValid() || !cluster.layoutKey().equals(renderable.layout().key())) {
            if (cluster != null) {
                cluster.remove();
            }
            cluster = spawn(renderable);
            clusters.put(displayId, cluster);
        }

        DisplayContent content = renderable.content();
        cluster.title.text(content.title());
        cluster.title.setRotation(renderable.anchor().yaw(), 0.0f);

        DisplayLayout layout = renderable.layout();
        for (int i = 0; i < layout.slotCount(); i++) {
            DisplayContent.DisplaySlot slot = findSlot(content, i);
            ItemDisplay background = cluster.backgrounds.get(i);
            ItemDisplay icon = cluster.icons.get(i);
            TextDisplay amount = cluster.amounts.get(i);

            background.setItemStack(slot != null && slot.background() != null
                    ? slot.background().clone()
                    : new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            background.setRotation(renderable.anchor().yaw(), 0.0f);

            if (slot == null) {
                icon.setItemStack(new ItemStack(Material.AIR));
                amount.text(Component.empty());
                icon.setRotation(renderable.anchor().yaw(), 0.0f);
                amount.setRotation(renderable.anchor().yaw(), 0.0f);
                continue;
            }

            icon.setItemStack(slot.icon());
            icon.setTransformation(new Transformation(
                    new Vector3f(),
                    new Quaternionf(),
                    new Vector3f(slot.scale(), slot.scale(), slot.scale()),
                    new Quaternionf()
            ));
            icon.setRotation(renderable.anchor().yaw(), 0.0f);
            amount.text(slot.amountText() == null ? Component.empty() : slot.amountText());
            amount.setRotation(renderable.anchor().yaw(), 0.0f);
            cluster.interactions.get(i).setInteractionHeight(slot.interactionHeight());
            cluster.interactions.get(i).setInteractionWidth(slot.interactionWidth());
        }
    }

    private void refreshAll() {
        List<UUID> ids = new ArrayList<>(renderables.keySet());
        for (UUID id : ids) {
            refresh(id);
        }
    }

    private Cluster spawn(DisplayRenderable renderable) {
        DisplayAnchor anchor = renderable.anchor();
        DisplayLayout layout = renderable.layout();
        Location titleLocation = transform(anchor.location(), 0.0, layout.titleOffsetY(), 0.0, anchor.yaw());

        TextDisplay title = anchor.location().getWorld().spawn(titleLocation, TextDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "title");
            entity.setBillboard(Display.Billboard.FIXED);
            entity.setRotation(anchor.yaw(), 0.0f);
        });

        List<BlockDisplay> standDisplays = spawnStand(renderable);
        List<Interaction> standInteractions = spawnStandInteractions(renderable);
        List<ItemDisplay> backgrounds = new ArrayList<>(layout.slotCount());
        List<ItemDisplay> icons = new ArrayList<>(layout.slotCount());
        List<TextDisplay> amounts = new ArrayList<>(layout.slotCount());
        List<Interaction> interactions = new ArrayList<>(layout.slotCount());

        for (int slot = 0; slot < layout.slotCount(); slot++) {
            int row = slot / layout.columns();
            int column = slot % layout.columns();
            double x = (-((layout.columns() - 1) * layout.slotSpacing()) / 2.0) + (column * layout.slotSpacing());
            double y = layout.originY() - (row * layout.slotSpacing());

            Location backgroundLocation = transform(anchor.location(), x, y, 0.0, anchor.yaw());
            ItemDisplay background = anchor.location().getWorld().spawn(backgroundLocation, ItemDisplay.class, entity -> {
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
            backgrounds.add(background);

            Location iconLocation = transform(anchor.location(), x, y + 0.06, 0.02, anchor.yaw());
            ItemDisplay icon = anchor.location().getWorld().spawn(iconLocation, ItemDisplay.class, entity -> {
                prepare(entity, renderable.uniqueId(), -1, "icon");
                entity.setBillboard(Display.Billboard.FIXED);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                entity.setTransformation(new Transformation(
                        new Vector3f(),
                        new Quaternionf(),
                        new Vector3f(layout.itemScale(), layout.itemScale(), layout.itemScale()),
                        new Quaternionf()
                ));
                entity.setRotation(anchor.yaw(), 0.0f);
            });
            icons.add(icon);

            Location amountLocation = transform(anchor.location(), x, y - 0.22, 0.02, anchor.yaw());
            TextDisplay amount = anchor.location().getWorld().spawn(amountLocation, TextDisplay.class, entity -> {
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
            amounts.add(amount);

            final int slotIndex = slot;
            Location interactionLocation = transform(anchor.location(), x, y - 0.255, 0.0, anchor.yaw());
            Interaction interaction = anchor.location().getWorld().spawn(interactionLocation, Interaction.class, entity -> {
                prepare(entity, renderable.uniqueId(), slotIndex, "slot");
                entity.setResponsive(true);
                entity.setInteractionHeight(0.50f);
                entity.setInteractionWidth(0.50f);
                entity.setRotation(anchor.yaw(), 0.0f);
            });
            interactions.add(interaction);
        }

        return new Cluster(layout.key(), title, standDisplays, standInteractions, backgrounds, icons, amounts, interactions);
    }

    private DisplayContent.DisplaySlot findSlot(DisplayContent content, int index) {
        for (DisplayContent.DisplaySlot slot : content.slots()) {
            if (slot.index() == index) {
                return slot;
            }
        }
        return null;
    }

    private List<BlockDisplay> spawnStand(DisplayRenderable renderable) {
        DisplayAnchor anchor = renderable.anchor();
        DisplayLayout layout = renderable.layout();
        List<BlockDisplay> stands = new ArrayList<>(2);
        float poleHeight = layout.rows() <= 1 ? 1.0625f : 2.0625f;

        Location poleLocation = transform(anchor.location(), 0.0, 1.02, -0.04, anchor.yaw());
        BlockDisplay pole = anchor.location().getWorld().spawn(poleLocation, BlockDisplay.class, entity -> {
            prepare(entity, renderable.uniqueId(), -1, "stand");
            entity.setBlock(org.bukkit.Material.DARK_OAK_FENCE.createBlockData());
            entity.setTransformation(new Transformation(
                    new Vector3f(-0.11f, -1.0f, -0.11f),
                    new Quaternionf(),
                    new Vector3f(0.22f, poleHeight, 0.22f),
                    new Quaternionf()
            ));
            entity.setRotation(anchor.yaw(), 0.0f);
        });
        stands.add(pole);

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

    private Location transform(Location base, double localX, double localY, double localZ, float yaw) {
        double radians = Math.toRadians(-yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        double worldX = (localX * cos) - (localZ * sin);
        double worldZ = (localX * sin) + (localZ * cos);
        return base.clone().add(0.5 + worldX, localY, 0.5 + worldZ);
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

    private record Cluster(
            String layoutKey,
            TextDisplay title,
            List<BlockDisplay> standDisplays,
            List<Interaction> standInteractions,
            List<ItemDisplay> backgrounds,
            List<ItemDisplay> icons,
            List<TextDisplay> amounts,
            List<Interaction> interactions
    ) {
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
                if (display == null || !display.isValid()) {
                    return false;
                }
            }
            for (TextDisplay display : amounts) {
                if (display == null || !display.isValid()) {
                    return false;
                }
            }
            for (Interaction interaction : interactions) {
                if (interaction == null || !interaction.isValid()) {
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
