package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class WaccCalculatorTest {

    @Test
    void computesWeightedAverageCostOfCapital() {
        WaccResult result = new WaccCalculator().compute(new WaccInput(
                new BigDecimal("0.04"),
                new BigDecimal("0.05"),
                new BigDecimal("1.20"),
                new BigDecimal("0.06"),
                new BigDecimal("40"),
                new BigDecimal("60"),
                new BigDecimal("0.25"),
                new BigDecimal("0.09")));

        assertThat(result.fallbackUsed()).isFalse();
        assertThat(result.costOfEquity()).isEqualByComparingTo("0.100000");
        assertThat(result.debtWeight()).isEqualByComparingTo("0.400000");
        assertThat(result.equityWeight()).isEqualByComparingTo("0.600000");
        assertThat(result.wacc()).isEqualByComparingTo("0.078000");
    }

    @Test
    void fallsBackWhenBetaIsUnavailable() {
        WaccResult result = new WaccCalculator().compute(new WaccInput(
                new BigDecimal("0.04"),
                new BigDecimal("0.05"),
                null,
                null,
                new BigDecimal("40"),
                new BigDecimal("60"),
                null,
                new BigDecimal("0.09")));

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.wacc()).isEqualByComparingTo("0.09");
        assertThat(result.source()).isEqualTo("sector-fallback");
    }
}
