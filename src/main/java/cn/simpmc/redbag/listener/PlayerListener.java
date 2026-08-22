package cn.simpmc.redbag.listener;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.gui.RedPacketCreationSession;
import cn.simpmc.redbag.util.SchedulerCompat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public final class PlayerListener implements Listener {

    private final SimpMCRedBag plugin;

    public PlayerListener(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        if (inventory.getSize() != 27
                || !plugin.getConfigManager().getGuiTitle().equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == inventory) {
            plugin.getRedPacketGUI().handleClick(player, event.getSlot(), event.getCurrentItem());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        RedPacketCreationSession session = plugin.getRedPacketGUI()
                .getCreationSession(player.getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        SchedulerCompat.runGlobal(plugin, () -> plugin.getRedPacketGUI()
                .handleChatInput(player, event.getMessage()));
    }

    /** Backward-compatible interception for old clickable messages. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().trim();
        String lower = message.toLowerCase(java.util.Locale.ROOT);
        if (!lower.startsWith("/bshongbao_claim ")) {
            return;
        }
        String[] parts = message.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        event.setCancelled(true);
        plugin.getChatManager().handleClaimCommand(event.getPlayer(), parts[1]);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        SchedulerCompat.runLaterGlobal(plugin,
                () -> plugin.getRedPacketManager().processRefund(event.getPlayer()), 20L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getRedPacketGUI().removeCreationSession(event.getPlayer().getUniqueId());
    }
}
