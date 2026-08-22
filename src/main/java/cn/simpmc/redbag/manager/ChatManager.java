package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.model.RedPacket;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.math.BigDecimal;

public final class ChatManager {

    private final SimpMCRedBag plugin;

    public ChatManager(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    public void broadcastRedPacket(RedPacket packet) {
        String template = plugin.getConfigManager().getRawMessage("messages.chat.packet-sent");
        String message = template.replace("{player}", packet.getSenderName())
                .replace("{type}", packet.getType().getDisplayName());
        String marker = "[点击领取]";
        String plain = message.replace(marker, "");
        TextComponent full = new TextComponent(plain);
        TextComponent clickable = new TextComponent(marker);
        clickable.setColor(net.md_5.bungee.api.ChatColor.GREEN);
        clickable.setBold(true);
        clickable.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/redbag claim " + packet.getId()));
        clickable.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("点击领取红包\n")
                        .color(net.md_5.bungee.api.ChatColor.YELLOW)
                        .append("类型: " + packet.getType().getDisplayName() + "\n")
                        .color(net.md_5.bungee.api.ChatColor.WHITE)
                        .append("红包池: " + plugin.getEconomyManager().formatAmount(packet.getDistributedAmount()) + "\n")
                        .append("余数返还: " + plugin.getEconomyManager().formatAmount(packet.getRemainderAmount()) + "\n")
                        .append("总份数: " + packet.getTotalCount() + "\n")
                        .append("剩余: " + packet.getRemainingCount() + " 份")
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .create()));
        full.addExtra(clickable);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.spigot().sendMessage(full);
        }
    }

    public void broadcastPacketClaimed(RedPacket packet, String claimer, BigDecimal amount) {
        String message = plugin.getConfigManager().getRawMessage("messages.chat.packet-claimed")
                .replace("{claimer}", claimer)
                .replace("{sender}", packet.getSenderName())
                .replace("{type}", packet.getType().getDisplayName())
                .replace("{amount}", plugin.getEconomyManager().formatAmount(amount));
        Bukkit.broadcastMessage(message);
    }

    public boolean handleClaimCommand(Player player, String packetId) {
        if (player == null || packetId == null || packetId.isBlank()) {
            return true;
        }
        RedPacket packet = plugin.getRedPacketManager().getRedPacket(packetId);
        if (packet == null) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.packet-not-found"));
            return true;
        }
        if (packet.getSenderId().equals(player.getUniqueId().toString())) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.cannot-claim-own"));
            return true;
        }
        if (packet.getClaimedPlayers().containsKey(player.getUniqueId().toString())) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.already-claimed"));
            return true;
        }
        if (packet.isFullyClaimed()) {
            player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.packet-complete"));
            return true;
        }

        BigDecimal amount = plugin.getRedPacketManager().claimAndDeposit(packetId, player);
        if (amount == null) {
            if (packet.isExpired()) {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.packet-not-found"));
            } else {
                player.sendMessage(plugin.getConfigManager().getMessage("messages.errors.operation-failed"));
            }
            return true;
        }
        player.sendMessage(plugin.getConfigManager().getMessage(
                "messages.success.packet-claimed", "{amount}",
                plugin.getEconomyManager().formatAmount(amount)));
        broadcastPacketClaimed(packet, player.getName(), amount);
        return true;
    }

    public void sendExpirationNotice(Player player, BigDecimal refundAmount) {
        player.sendMessage(plugin.getConfigManager().getMessage(
                "messages.chat.packet-expired", "{amount}",
                plugin.getEconomyManager().formatAmount(refundAmount)));
    }

    public void sendCreatedMessage(Player player, RedPacketService.Result result) {
        player.sendMessage(plugin.getConfigManager().getMessage(
                "messages.success.packet-created",
                "{amount}", plugin.getEconomyManager().formatAmount(result.amount()),
                "{charged}", plugin.getEconomyManager().formatAmount(result.charged()),
                "{returned}", plugin.getEconomyManager().formatAmount(result.returnedAmount()),
                "{count}", String.valueOf(result.packet().getTotalCount())));
        if (result.tax().signum() > 0) {
            player.sendMessage(plugin.getConfigManager().getMessage(
                    "messages.chat.tax-collected",
                    "{tax}", plugin.getEconomyManager().formatAmount(result.tax()),
                    "{recipient}", plugin.getTaxManager().recipientName()));
        }
    }

    public void sendResultError(Player player, RedPacketService.Result result) {
        String path;
        String[] replacements = new String[0];
        switch (result.status()) {
            case AMOUNT_TOO_LOW -> {
                path = "messages.errors.amount-too-low";
                replacements = new String[] {"{min}", plugin.getEconomyManager().formatAmount(
                        plugin.getConfigManager().getMinTotalAmount())};
            }
            case COUNT_TOO_HIGH -> {
                path = "messages.errors.count-too-high";
                replacements = new String[] {"{max}", String.valueOf(plugin.getConfigManager().getMaxPacketCount())};
            }
            case COUNT_TOO_LARGE_FOR_AMOUNT -> {
                path = "messages.errors.count-too-large-for-amount";
                replacements = new String[] {"{amount}", plugin.getEconomyManager().formatAmount(result.amount()),
                        "{max}", String.valueOf(result.maximumCount())};
            }
            case INSUFFICIENT_FUNDS -> {
                path = "messages.errors.insufficient-funds";
                replacements = new String[] {"{amount}", plugin.getEconomyManager().formatAmount(result.charged())};
            }
            case COOLDOWN -> {
                path = "messages.errors.send-cooldown";
                replacements = new String[] {"{seconds}", String.valueOf(result.remainingSeconds())};
            }
            case TOO_MANY_ACTIVE -> {
                path = "messages.errors.too-many-active";
                replacements = new String[] {"{max}", String.valueOf(plugin.getConfigManager().getMaxActivePerSender())};
            }
            case TAX_FAILED -> path = "messages.errors.tax-failed";
            case INVALID_COUNT -> path = "messages.errors.invalid-count";
            case INVALID_AMOUNT -> path = "messages.errors.invalid-amount";
            default -> path = "messages.errors.operation-failed";
        }
        player.sendMessage(plugin.getConfigManager().getMessage(path, replacements));
    }

    public String formatMessage(String message) {
        return ChatColor.translateAlternateColorCodes('&', message == null ? "" : message);
    }

    public void sendPrefixedMessage(Player player, String message) {
        player.sendMessage(plugin.getConfigManager().getPrefix() + formatMessage(message));
    }

    public void broadcastPrefixedMessage(String message) {
        Bukkit.broadcastMessage(plugin.getConfigManager().getPrefix() + formatMessage(message));
    }
}
