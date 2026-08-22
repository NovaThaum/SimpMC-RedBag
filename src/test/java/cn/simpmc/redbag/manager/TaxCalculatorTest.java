package cn.simpmc.redbag.manager;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxCalculatorTest {

    @Test
    void calculatesConfiguredTaxAndPlayerCharge() {
        TaxCalculator calculator = new TaxCalculator(new BigDecimal("0.10"));
        assertEquals(new BigDecimal("1.00"), calculator.tax(new BigDecimal("10.00")));
        assertEquals(new BigDecimal("11.00"), calculator.charge(new BigDecimal("10.00")));
    }

    @Test
    void keepsHighPrecisionRateUntilCurrencyRounding() {
        TaxCalculator calculator = new TaxCalculator(new BigDecimal("0.055"));
        assertEquals(new BigDecimal("0.55"), calculator.tax(new BigDecimal("10.00")));
        assertEquals(new BigDecimal("10.55"), calculator.charge(new BigDecimal("10.00")));
    }
}
