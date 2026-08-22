package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.model.RedPacket;
import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class RedPacketManager {

    private final SimpMCRedBag plugin;
    private final Map<String, RedPacket> activePackets = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<BigDecimal>> pendingRefunds = new ConcurrentHashMap<>();

    public RedPacketManager(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    public RedPacket createRedPacket(
            String senderId,
            String senderName,
            RedPacket.RedPacketType type,
            BigDecimal totalAmount,
            int count) {
        RedPacket packet = new RedPacket(
                senderId,
                senderName,
                type,
                totalAmount,
                count,
                plugin.getConfigManager().getExpirationMinutes());
        register(packet);
        return packet;
    }

    public void register(RedPacket packet) {
        activePackets.put(packet.getId(), packet);
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Created red packet " + packet.getId() + " by "
                    + packet.getSenderName());
        }
    }

    /** Claim and deposit as one logical operation; no payout is lost on Vault failure. */
    public BigDecimal claimAndDeposit(String packetId, Player player) {
        RedPacket packet = activePackets.get(packetId);
        if (packet == null || player == null || packet.isExpired()) {
            if (packet != null && packet.isExpired()) {
                processExpiredPacket(packetId);
            }
            return null;
        }
        String playerId = player.getUniqueId().toString();
        // Keep reservation, Vault settlement, and rollback under the packet
        // monitor. Expiry/refund code uses the same monitor, so it cannot
        // refund a packet while a claim is being settled.
        synchronized (packet) {
            BigDecimal amount = packet.claim(playerId);
            if (amount == null) {
                return null;
            }
            if (!plugin.getEconomyManager().deposit(player, amount)) {
                packet.rollbackClaim(playerId);
                return null;
            }
            if (packet.isFullyClaimed()) {
                returnRemainder(packet, findOnlinePlayer(packet.getSenderId()));
                activePackets.remove(packetId, packet);
            }
            return amount;
        }
    }

    public RedPacket getRedPacket(String packetId) {
        return activePackets.get(packetId);
    }

    public boolean hasRedPacket(String packetId) {
        return activePackets.containsKey(packetId);
    }

    public int countBySender(String senderId) {
        int count = 0;
        for (RedPacket packet : activePackets.values()) {
            if (packet.getSenderId().equals(senderId) && !packet.isExpired()) {
                count++;
            }
        }
        return count;
    }

    public void processExpiredPacket(String packetId) {
        RedPacket packet = activePackets.remove(packetId);
        if (packet == null) {
            return;
        }
        synchronized (packet) {
            if (!packet.isExpired()) {
                activePackets.putIfAbsent(packetId, packet);
                return;
            }
            BigDecimal remaining = packet.getRemainingAmount();
            if (remaining.signum() > 0) {
                addPendingRefund(packet.getSenderId(), remaining);
                try {
                    Player sender = Bukkit.getPlayer(UUID.fromString(packet.getSenderId()));
                    if (sender != null && sender.isOnline()) {
                        processRefund(sender);
                    }
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Cannot resolve sender UUID for refund: "
                            + packet.getSenderId());
                }
            }
            packet.takeRemainderForRefund();
            packet.setExpired();
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("Expired red packet " + packet.getId()
                        + ", refunding " + remaining);
            }
        }
    }

    public List<RedPacket> getExpiredPackets() {
        List<RedPacket> expired = new ArrayList<>();
        for (RedPacket packet : activePackets.values()) {
            if (packet.isExpired()) {
                expired.add(packet);
            }
        }
        return expired;
    }

    public void removeRedPacket(String packetId) {
        activePackets.remove(packetId);
    }

    public void addPendingRefund(String playerId, BigDecimal amount) {
        if (playerId == null || !MoneyMath.isPositive(amount)) {
            return;
        }
        synchronized (pendingRefunds) {
            ConcurrentLinkedQueue<BigDecimal> refunds = pendingRefunds.computeIfAbsent(
                    playerId, ignored -> new ConcurrentLinkedQueue<>());
            refunds.add(MoneyMath.normalize(amount));
        }
    }

    /** Returns the normal-packet remainder exactly once, or queues it for retry. */
    public BigDecimal returnRemainder(RedPacket packet, Player sender) {
        if (packet == null) {
            return BigDecimal.ZERO.setScale(MoneyMath.SCALE);
        }
        BigDecimal remainder = packet.takeRemainderForRefund();
        if (remainder.signum() == 0) {
            return remainder;
        }
        if (sender != null && sender.isOnline()
                && plugin.getEconomyManager().deposit(sender, remainder)) {
            return remainder;
        }
        addPendingRefund(packet.getSenderId(), remainder);
        return remainder;
    }

    public void processRefund(Player player) {
        if (player == null) {
            return;
        }
        String playerId = player.getUniqueId().toString();
        synchronized (pendingRefunds) {
            ConcurrentLinkedQueue<BigDecimal> refunds = pendingRefunds.get(playerId);
            if (refunds == null || refunds.isEmpty()) {
                return;
            }
            BigDecimal total = refunds.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) {
                pendingRefunds.remove(playerId, refunds);
                return;
            }
            if (plugin.getEconomyManager().deposit(player, total)) {
                pendingRefunds.remove(playerId, refunds);
                plugin.getChatManager().sendExpirationNotice(player, total);
            } else {
                plugin.getLogger().warning("Unable to process refund " + total + " for "
                        + player.getName());
            }
        }
    }

    /** Refund every active packet during a clean plugin shutdown/reload. */
    public void refundAllActivePackets() {
        for (String packetId : new ArrayList<>(activePackets.keySet())) {
            RedPacket packet = activePackets.get(packetId);
            if (packet != null) {
                packet.setExpired();
                processExpiredPacket(packetId);
            }
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            processRefund(player);
        }
        settleOfflineRefunds();
        if (!pendingRefunds.isEmpty()) {
            plugin.getLogger().warning("Pending refunds could not be settled for " + pendingRefunds.size()
                    + " player(s); enable a persistent economy provider and retry shutdown.");
        }
    }

    private void settleOfflineRefunds() {
        synchronized (pendingRefunds) {
            for (Map.Entry<String, ConcurrentLinkedQueue<BigDecimal>> entry
                    : new ArrayList<>(pendingRefunds.entrySet())) {
            UUID id;
            try {
                id = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid refund player UUID: " + entry.getKey());
                continue;
            }
                ConcurrentLinkedQueue<BigDecimal> refunds = entry.getValue();
                BigDecimal total = refunds.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(id);
                // Vault's EconomyResponse is checked directly because this player
                // may be offline during plugin shutdown.
                var response = plugin.getEconomyManager().deposit(offlinePlayer, total);
                if (response != null && response.transactionSuccess()) {
                    pendingRefunds.remove(entry.getKey(), refunds);
                }
            }
        }
    }

    public void processAllRefunds() {
        for (RedPacket packet : getExpiredPackets()) {
            processExpiredPacket(packet.getId());
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            processRefund(player);
        }
    }

    public int getActivePacketCount() {
        return activePackets.size();
    }

    public Collection<RedPacket> getActivePackets() {
        return new ArrayList<>(activePackets.values());
    }

    public int getPendingRefundCount(String playerId) {
        synchronized (pendingRefunds) {
            ConcurrentLinkedQueue<BigDecimal> refunds = pendingRefunds.get(playerId);
            return refunds == null ? 0 : refunds.size();
        }
    }

    public BigDecimal getPendingRefundAmount(String playerId) {
        synchronized (pendingRefunds) {
            ConcurrentLinkedQueue<BigDecimal> refunds = pendingRefunds.get(playerId);
            if (refunds == null) {
                return BigDecimal.ZERO.setScale(MoneyMath.SCALE);
            }
            return MoneyMath.normalize(refunds.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    public void cleanup() {
        refundAllActivePackets();
        activePackets.clear();
    }

    private Player findOnlinePlayer(String playerId) {
        try {
            return Bukkit.getPlayer(UUID.fromString(playerId));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
