package cn.simpmc.redbag.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Currency helpers. A red packet is always represented as whole cents. */
public final class MoneyMath {

    public static final int SCALE = 2;
    public static final BigDecimal CENT = new BigDecimal("0.01");

    private MoneyMath() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static long toCentsExact(BigDecimal amount) {
        return normalize(amount).movePointRight(SCALE).longValueExact();
    }

    public static BigDecimal fromCents(long cents) {
        return BigDecimal.valueOf(cents, SCALE);
    }

    public static boolean isPositive(BigDecimal amount) {
        return amount != null && normalize(amount).signum() > 0;
    }
}
