package cn.simpmc.redbag.model;

import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * An in-memory red packet. Payouts are generated in cents. Normal packets use
 * one identical amount per claim; a non-divisible remainder is returned to the
 * sender instead of being assigned to a player.
 */
public final class RedPacket {

    private final String id = UUID.randomUUID().toString();
    private final String senderId;
    private final String senderName;
    private final RedPacketType type;
    private final BigDecimal totalAmount;
    private final int totalCount;
    private final long createTime;
    private final long expireTime;
    private final List<BigDecimal> amounts;
    private final Deque<BigDecimal> availableAmounts;
    private final Map<String, BigDecimal> claimedPlayers;
    private final BigDecimal remainderAmount;
    private boolean remainderReturned;
    private boolean expired;

    public RedPacket(
            String senderId,
            String senderName,
            RedPacketType type,
            BigDecimal totalAmount,
            int totalCount,
            long expireTimeMinutes) {
        this(senderId, senderName, type, totalAmount, totalCount, expireTimeMinutes, new Random());
    }

    public RedPacket(
            String senderId,
            String senderName,
            RedPacketType type,
            BigDecimal totalAmount,
            int totalCount,
            long expireTimeMinutes,
            Random random) {
        if (senderId == null || senderId.isBlank()) {
            throw new IllegalArgumentException("senderId must not be blank");
        }
        if (senderName == null || senderName.isBlank()) {
            throw new IllegalArgumentException("senderName must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (totalCount <= 0) {
            throw new IllegalArgumentException("totalCount must be positive");
        }
        if (expireTimeMinutes <= 0) {
            throw new IllegalArgumentException("expireTimeMinutes must be positive");
        }
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }

        BigDecimal normalized = MoneyMath.normalize(totalAmount);
        if (!MoneyMath.isPositive(normalized)) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }
        long cents;
        try {
            cents = MoneyMath.toCentsExact(normalized);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("totalAmount is outside the supported range", exception);
        }
        if (cents < totalCount) {
            throw new IllegalArgumentException("each packet must contain at least one cent");
        }

        this.senderId = senderId;
        this.senderName = senderName;
        this.type = type;
        this.totalAmount = normalized;
        this.totalCount = totalCount;
        this.createTime = System.currentTimeMillis();
        this.expireTime = this.createTime + expireTimeMinutes * 60L * 1000L;
        this.amounts = new ArrayList<>(totalCount);
        this.availableAmounts = new ArrayDeque<>(totalCount);
        this.claimedPlayers = new HashMap<>();
        this.remainderAmount = type == RedPacketType.NORMAL
                ? MoneyMath.fromCents(cents % totalCount)
                : BigDecimal.ZERO.setScale(MoneyMath.SCALE);
        this.remainderReturned = this.remainderAmount.signum() == 0;
        this.expired = false;

        if (type == RedPacketType.NORMAL) {
            generateEvenAmounts(cents);
        } else {
            generateLuckyAmounts(cents, random);
        }
        availableAmounts.addAll(amounts);
    }

    private void generateEvenAmounts(long totalCents) {
        long base = totalCents / totalCount;
        for (int index = 0; index < totalCount; index++) {
            amounts.add(MoneyMath.fromCents(base));
        }
    }

    private void generateLuckyAmounts(long totalCents, Random random) {
        long remainingCents = totalCents;
        int remainingCount = totalCount;
        while (remainingCount > 1) {
            // Leave one cent for every packet still to be generated. The
            // upper bound keeps the distribution close to the original
            // "lucky" behavior while retaining exact-cent accounting.
            long safeMaximum = remainingCents - (remainingCount - 1L);
            long averageMaximum = Math.max(1L, remainingCents / remainingCount * 2L);
            long maximum = Math.min(safeMaximum, averageMaximum);
            long cents = nextLong(random, maximum) + 1L;
            amounts.add(MoneyMath.fromCents(cents));
            remainingCents -= cents;
            remainingCount--;
        }
        amounts.add(MoneyMath.fromCents(remainingCents));
        Collections.shuffle(amounts, random);
    }

    private static long nextLong(Random random, long boundExclusive) {
        if (boundExclusive <= 1L) {
            return 0L;
        }
        return random.nextLong(boundExclusive);
    }

    public synchronized BigDecimal claim(String playerId) {
        if (playerId == null || playerId.isBlank() || expired || isExpired()) {
            return null;
        }
        if (claimedPlayers.containsKey(playerId) || availableAmounts.isEmpty()) {
            return null;
        }
        BigDecimal amount = availableAmounts.removeFirst();
        claimedPlayers.put(playerId, amount);
        return amount;
    }

    /** Undo a reservation when the economy provider rejects the payout. */
    public synchronized boolean rollbackClaim(String playerId) {
        BigDecimal amount = claimedPlayers.remove(playerId);
        if (amount == null) {
            return false;
        }
        availableAmounts.addFirst(amount);
        return true;
    }

    public synchronized boolean isExpired() {
        return expired || System.currentTimeMillis() >= expireTime;
    }

    public synchronized void setExpired() {
        expired = true;
    }

    public synchronized BigDecimal getRemainingAmount() {
        BigDecimal availableAmount = availableAmounts.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!remainderReturned) {
            availableAmount = availableAmount.add(remainderAmount);
        }
        return MoneyMath.normalize(availableAmount);
    }

    public synchronized int getRemainingCount() {
        return availableAmounts.size();
    }

    public synchronized boolean isFullyClaimed() {
        return availableAmounts.isEmpty();
    }

    public BigDecimal getRemainderAmount() {
        return remainderAmount;
    }

    public synchronized BigDecimal getUnreturnedRemainderAmount() {
        return remainderReturned ? BigDecimal.ZERO.setScale(MoneyMath.SCALE) : remainderAmount;
    }

    /** Marks the normal-packet remainder as returned, so it cannot be refunded twice. */
    public synchronized BigDecimal takeRemainderForRefund() {
        if (remainderReturned) {
            return BigDecimal.ZERO.setScale(MoneyMath.SCALE);
        }
        remainderReturned = true;
        return remainderAmount;
    }

    public String getId() {
        return id;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public RedPacketType getType() {
        return type;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /** The amount that is actually distributed among packet claims. */
    public synchronized BigDecimal getDistributedAmount() {
        return MoneyMath.normalize(totalAmount.subtract(remainderAmount));
    }

    public int getTotalCount() {
        return totalCount;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getExpireTime() {
        return expireTime;
    }

    public synchronized Map<String, BigDecimal> getClaimedPlayers() {
        return new HashMap<>(claimedPlayers);
    }

    public enum RedPacketType {
        NORMAL("普通红包"),
        LUCKY("拼手气红包");

        private final String displayName;

        RedPacketType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
