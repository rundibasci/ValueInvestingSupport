package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdmCalculatorTest {

    @Test
    void happyPath_ko_era() {
        // KO: DPS=1.84, g=5%, r=8% → 1.84 / 0.03 = 61.33
        BigDecimal result = DdmCalculator.calculate(
                new BigDecimal("1.84"),
                new BigDecimal("0.05"),
                new BigDecimal("0.08"),
                60);
        assertThat(result).isEqualByComparingTo("61.33");
    }

    @Test
    void happyPath_round_numbers() {
        // DPS=2.00, g=4%, r=9% → 2.00 / 0.05 = 40.00
        BigDecimal result = DdmCalculator.calculate(
                new BigDecimal("2.00"),
                new BigDecimal("0.04"),
                new BigDecimal("0.09"),
                10);
        assertThat(result).isEqualByComparingTo("40.00");
    }

    @Test
    void resultHasScale2() {
        BigDecimal result = DdmCalculator.calculate(
                new BigDecimal("2.00"),
                new BigDecimal("0.04"),
                new BigDecimal("0.09"),
                10);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    void rule07_ineligible_4years_throws() {
        assertThatThrownBy(() -> DdmCalculator.calculate(
                new BigDecimal("1.84"),
                new BigDecimal("0.05"),
                new BigDecimal("0.08"),
                4))
                .isInstanceOf(DdmNotEligibleException.class);
    }

    @Test
    void rule07_boundary_exactly5years_passes() {
        BigDecimal result = DdmCalculator.calculate(
                new BigDecimal("1.84"),
                new BigDecimal("0.05"),
                new BigDecimal("0.08"),
                5);
        assertThat(result).isNotNull();
    }

    @Test
    void requiredReturnEqualsGrowthThrows() {
        assertThatThrownBy(() -> DdmCalculator.calculate(
                new BigDecimal("1.84"),
                new BigDecimal("0.05"),
                new BigDecimal("0.05"),
                10))
                .isInstanceOf(DdmNotApplicableException.class);
    }

    @Test
    void requiredReturnLessThanGrowthThrows() {
        assertThatThrownBy(() -> DdmCalculator.calculate(
                new BigDecimal("1.84"),
                new BigDecimal("0.05"),
                new BigDecimal("0.04"),
                10))
                .isInstanceOf(DdmNotApplicableException.class);
    }
}
