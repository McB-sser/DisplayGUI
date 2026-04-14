package de.mcbesser.displaygui.feature.crafting;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TitlePromptListener implements Listener {
    private final CraftingBannerManager manager;
    private final Map<UUID, UUID> pendingTitles = new HashMap<>();

    public TitlePromptListener(CraftingBannerManager manager) {
        this.manager = manager;
    }

    public void requestTitle(Player player, UUID bannerId) {
        pendingTitles.put(player.getUniqueId(), bannerId);
        player.closeInventory();
        player.sendMessage(Component.text("Schreibe den neuen Titel in den Chat. Mit 'cancel' abbrechen."));
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID bannerId = pendingTitles.remove(event.getPlayer().getUniqueId());
        if (bannerId == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (text.equalsIgnoreCase("cancel")) {
            event.getPlayer().sendMessage(Component.text("Titel-Eingabe abgebrochen."));
            return;
        }
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> manager.setBannerTitle(bannerId, text, event.getPlayer()));
    }
}
