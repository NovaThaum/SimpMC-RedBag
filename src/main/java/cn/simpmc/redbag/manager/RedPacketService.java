package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.SimpMCRedBag;
import cn.simpmc.redbag.model.RedPacket;
import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

/** Shared validation and settlement path used by both GUI and /redbag send. */
public final class RedPacketService {

    private final SimpMCRedBag plugin;
    private final Map<UUID, Long> lastSuccessfulSend = new ConcurrentHashMap<>();

    public RedPacketService(SimpMCRedBag plugin) {
        this.plugin = plugin;
    }

    public Result send(Player sender, RedPacket.RedPacketType type, BigDecimal requested, int count) {
        synchronized (lastSuccessfulSend) {
            return sendLocked(sender, type, requested, count);
        }
    }

    private Result sendLocked(Player sender, RedPacket.RedPacketType type,
            BigDecimal requested, int count) {
        if (sender == null || type == null || requested == null) {
            return Result.failure(Status.INVALID_AMOUNT);
        }
        BigDecimal amount;
        try {
            amount = requested.setScale(MoneyMath.SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            return Result.failure(Status.INVALID_AMOUNT);
        }
        if (!MoneyMath.isPositive(amount)) {
            return Result.failure(Status.INVALID_AMOUNT);
        }
        ConfigManager config = plugin.getConfigManager();
        if (amount.compareTo(config.getMinTotalAmount()) < 0) {
            return Result.failure(Status.AMOUNT_TOO_LOW, amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (count <= 0) {
            return Result.failure(Status.INVALID_COUNT, amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        if (count > config.getMaxPacketCount()) {
            return Result.failure(Status.COUNT_TOO_HIGH, amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        long cents;
        try {
            cents = MoneyMath.toCentsExact(amount);
        } catch (ArithmeticException exception) {
            return Result.failure(Status.INVALID_AMOUNT);
        }
        long minimumSingleCents;
        try {
            minimumSingleCents = MoneyMath.toCentsExact(config.getMinSingleAmount());
        } catch (ArithmeticException exception) {
            return Result.failure(Status.INVALID_AMOUNT, amount, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        long maximumCount = cents / Math.max(1L, minimumSingleCents);
        if (count > maximumCount) {
            return Result.failure(Status.COUNT_TOO_LARGE_FOR_AMOUNT, amount, BigDecimal.ZERO, BigDecimal.ZERO)
                    .withMaximumCount(maximumCount);
        }

        UUID senderId = sender.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMillis = config.getSendCooldownSeconds() * 1000L;
        Long previous = lastSuccessfulSend.get(senderId);
        if (previous != null && cooldownMillis > 0 && now - previous < cooldownMillis) {
            long seconds = Math.max(1L, (cooldownMillis - (now - previous) + 999L) / 1000L);
            return Result.failure(Status.COOLDOWN).withRemainingSeconds(seconds);
        }
        if (plugin.getRedPacketManager().countBySender(senderId.toString())
                >= config.getMaxActivePerSender()) {
            return Result.failure(Status.TOO_MANY_ACTIVE).withMaximumCount(
                    config.getMaxActivePerSender());
        }

        BigDecimal tax = plugin.getTaxManager().calculateTax(amount);
        BigDecimal charge = plugin.getTaxManager().calculateCharge(amount);
        if (!plugin.getEconomyManager().hasEnough(sender, charge)) {
            return Result.failure(Status.INSUFFICIENT_FUNDS, amount, tax, charge);
        }

        // Construct before charging so all amount/count validation is complete;
        // a failed Vault operation never leaves a phantom active packet.
        RedPacket packet;
        try {
            packet = new RedPacket(senderId.toString(), sender.getName(), type, amount, count,
                    config.getExpirationMinutes());
        } catch (IllegalArgumentException exception) {
            return Result.failure(Status.INVALID_AMOUNT, amount, tax, charge);
        }
        if (!plugin.getTaxManager().collect(sender, amount)) {
            return Result.failure(Status.TAX_FAILED, amount, tax, charge);
        }

        BigDecimal returned = plugin.getRedPacketManager().returnRemainder(packet, sender);
        plugin.getRedPacketManager().register(packet);
        lastSuccessfulSend.put(senderId, now);
        return Result.success(packet, amount, tax, charge, returned);
    }

    public BigDecimal chargeFor(BigDecimal amount) {
        return plugin.getTaxManager().calculateCharge(amount);
    }

    public record Result(
            Status status,
            RedPacket packet,
            BigDecimal amount,
            BigDecimal tax,
            BigDecimal charged,
            BigDecimal returnedAmount,
            long maximumCount,
            long remainingSeconds) {

        static Result success(RedPacket packet, BigDecimal amount, BigDecimal tax,
                BigDecimal charged, BigDecimal returnedAmount) {
            return new Result(Status.SUCCESS, packet, amount, tax, charged, returnedAmount, 0, 0);
        }

        static Result failure(Status status) {
            return new Result(status, null, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        }

        static Result failure(Status status, BigDecimal amount, BigDecimal tax, BigDecimal charged) {
            return new Result(status, null, amount, tax, charged, BigDecimal.ZERO, 0, 0);
        }

        Result withMaximumCount(long maximum) {
            return new Result(status, packet, amount, tax, charged, returnedAmount,
                    maximum, remainingSeconds);
        }

        Result withRemainingSeconds(long seconds) {
            return new Result(status, packet, amount, tax, charged, returnedAmount,
                    maximumCount, seconds);
        }

        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }
    }

    public enum Status {
        SUCCESS,
        INVALID_AMOUNT,
        AMOUNT_TOO_LOW,
        INVALID_COUNT,
        COUNT_TOO_HIGH,
        COUNT_TOO_LARGE_FOR_AMOUNT,
        INSUFFICIENT_FUNDS,
        TAX_FAILED,
        COOLDOWN,
        TOO_MANY_ACTIVE
    }
}
