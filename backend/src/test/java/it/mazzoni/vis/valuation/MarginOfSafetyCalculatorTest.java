package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MarginOfSafetyCalculatorTest {

    @Test
    void positiveMoS() {
        // (100 - 85) / 100 × 100 = 15.00
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100"), new BigDecimal("85"));
        assertThat(result).isEqualByComparingTo("15.00");
    }

    @Test
    void negativeMoS_overvalued() {
        // (100 - 110) / 100 × 100 = -10.00
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100"), new BigDecimal("110"));
        assertThat(result).isEqualByComparingTo("-10.00");
    }

    @Test
    void zeroMoS_fairValueEqualsPrice() {
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100"), new BigDecimal("100"));
        assertThat(result).isEqualByComparingTo("0.00");
    }

    @Test
    void resultHasScale2() {
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100"), new BigDecimal("85"));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void nullFairValueReturnsNull() {
        assertThat(MarginOfSafetyCalculator.compute(null, new BigDecimal("85"))).isNull();
    }

    @Test
    void nullPriceReturnsNull() {
        assertThat(MarginOfSafetyCalculator.compute(new BigDecimal("100"), null)).isNull();
    }

    @Test
    void zeroFairValueReturnsNull() {
        assertThat(MarginOfSafetyCalculator.compute(BigDecimal.ZERO, new BigDecimal("85"))).isNull();
    }

    @Test
    void negativeFairValueReturnsNull() {
        assertThat(MarginOfSafetyCalculator.compute(new BigDecimal("-50"), new BigDecimal("85"))).isNull();
    }

    @Test
    void zeroPriceGivesFullMoS() {
        // (100 - 0) / 100 × 100 = 100.00
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100"), BigDecimal.ZERO);
        assertThat(result).isEqualByComparingTo("100.00");
    }

    @Test
    void repeatingDecimalRoundsCorrectly() {
        // (30 - 10) / 30 × 100 = 200/3 = 66.666… → 66.67 (rounds up)
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("30"), new BigDecimal("10"));
        assertThat(result).isEqualByComparingTo("66.67");
    }

    @Test
    void nearZeroMoS() {
        // (100.01 - 100) / 100.01 × 100 = 0.009999… → 0.01
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("100.01"), new BigDecimal("100"));
        assertThat(result).isEqualByComparingTo("0.01");
    }

    @Test
    void highPrecisionFractionalInputs() {
        // (33.33 - 25.00) / 33.33 × 100 = 8.33/33.33×100 = 24.9925… → 24.99
        BigDecimal result = MarginOfSafetyCalculator.compute(
                new BigDecimal("33.33"), new BigDecimal("25.00"));
        assertThat(result).isEqualByComparingTo("24.99");
    }
}
