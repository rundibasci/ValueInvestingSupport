package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GrahamCalculatorTest {

    @Test
    void happyPath_eps5_bvps20() {
        // √(22.5 × 5 × 20) = √2250 ≈ 47.43
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("20"));
        assertThat(result).isEqualByComparingTo("47.43");
    }

    @Test
    void happyPath_eps4_bvps9() {
        // √(22.5 × 4 × 9) = √810 ≈ 28.46
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("4"), new BigDecimal("9"));
        assertThat(result).isEqualByComparingTo("28.46");
    }

    @Test
    void resultHasScale2() {
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("20"));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void nullEpsReturnsNull() {
        assertThat(GrahamCalculator.calculate(null, new BigDecimal("20"))).isNull();
    }

    @Test
    void nullBvpsReturnsNull() {
        assertThat(GrahamCalculator.calculate(new BigDecimal("5"), null)).isNull();
    }

    @Test
    void zeroEpsReturnsNull() {
        assertThat(GrahamCalculator.calculate(BigDecimal.ZERO, new BigDecimal("20"))).isNull();
    }

    @Test
    void negativeBvpsReturnsNull() {
        assertThat(GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("-1"))).isNull();
    }

    @Test
    void negativeEpsReturnsNull() {
        assertThat(GrahamCalculator.calculate(new BigDecimal("-3"), new BigDecimal("20"))).isNull();
    }

    @Test
    void bothNullReturnsNull() {
        assertThat(GrahamCalculator.calculate(null, null)).isNull();
    }

    @Test
    void roundsUp_eps2_bvps2() {
        // √(22.5 × 2 × 2) = √90 = 9.4868… → 9.49 (3rd decimal 6 ≥ 5)
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("2"), new BigDecimal("2"));
        assertThat(result).isEqualByComparingTo("9.49");
    }

    @Test
    void fractionalInputs_eps1point5_bvps12() {
        // √(22.5 × 1.5 × 12) = √405 = 9√5 = 20.1246… → 20.12
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("1.5"), new BigDecimal("12"));
        assertThat(result).isEqualByComparingTo("20.12");
    }
}
