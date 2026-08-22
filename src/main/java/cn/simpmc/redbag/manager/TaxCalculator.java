package cn.simpmc.redbag.manager;

import cn.simpmc.redbag.util.MoneyMath;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure, testable implementation of x*y and x*(1+y) in currency precision. */
public final class TaxCalculator {

    private final BigDecimal rate;

    public TaxCalculator(BigDecimal rate) {
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("tax rate must be between 0 and 1");
        }
        this.rate = rate;
    }

    public BigDecimal rate() {
        return rate;
    }

    public BigDecimal tax(BigDecimal principal) {
        requirePrincipal(principal);
        return MoneyMath.normalize(MoneyMath.normalize(principal)
                .multiply(rate)
                .setScale(MoneyMath.SCALE, RoundingMode.HALF_UP));
    }

    public BigDecimal charge(BigDecimal principal) {
        requirePrincipal(principal);
        return MoneyMath.normalize(MoneyMath.normalize(principal).add(tax(principal)));
    }

    private static void requirePrincipal(BigDecimal principal) {
        if (!MoneyMath.isPositive(principal)) {
            throw new IllegalArgumentException("principal must be positive");
        }
    }
}
