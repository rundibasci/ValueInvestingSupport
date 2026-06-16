package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GrahamCalculatorTest {

    @Test
    void happyPath_eps5_bvps30() {
        // √(22.5 × 5 × 30) = √3375 ≈ 58.09
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("30"));
        assertThat(result).isEqualByComparingTo("58.09");
    }

    @Test
    void happyPath_eps5_bvps20() {
        // √(22.5 × 5 × 20) = √2250 ≈ 47.43
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("20"));
        assertThat(result).isEqualByComparingTo("47.43");
    }

    @Test
    void happyPath_aapl_era_eps6point11_bvps4point25() {
        // √(22.5 × 6.11 × 4.25) = √584.27 ≈ 24.17
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("6.11"), new BigDecimal("4.25"));
        assertThat(result).isEqualByComparingTo("24.17");
    }

    @Test
    void resultHasScale2() {
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("20"));
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void roundsUp_eps2_bvps2() {
        // √(22.5 × 2 × 2) = √90 = 9.4868… → 9.49
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("2"), new BigDecimal("2"));
        assertThat(result).isEqualByComparingTo("9.49");
    }

    @Test
    void fractionalInputs_eps1point5_bvps12() {
        // √(22.5 × 1.5 × 12) = √405 ≈ 20.12
        BigDecimal result = GrahamCalculator.calculate(new BigDecimal("1.5"), new BigDecimal("12"));
        assertThat(result).isEqualByComparingTo("20.12");
    }

    @Test
    void zeroEpsThrows() {
        assertThatThrownBy(() -> GrahamCalculator.calculate(BigDecimal.ZERO, new BigDecimal("20")))
                .isInstanceOf(GrahamNotApplicableException.class);
    }

    @Test
    void negativeEpsThrows() {
        assertThatThrownBy(() -> GrahamCalculator.calculate(new BigDecimal("-3"), new BigDecimal("20")))
                .isInstanceOf(GrahamNotApplicableException.class);
    }

    @Test
    void nullEpsThrows() {
        assertThatThrownBy(() -> GrahamCalculator.calculate(null, new BigDecimal("20")))
                .isInstanceOf(GrahamNotApplicableException.class);
    }

    @Test
    void negativeBvpsThrows() {
        assertThatThrownBy(() -> GrahamCalculator.calculate(new BigDecimal("5"), new BigDecimal("-1")))
                .isInstanceOf(GrahamNotApplicableException.class);
    }

    @Test
    void nullBvpsThrows() {
        assertThatThrownBy(() -> GrahamCalculator.calculate(new BigDecimal("5"), null))
                .isInstanceOf(GrahamNotApplicableException.class);
    }
}
