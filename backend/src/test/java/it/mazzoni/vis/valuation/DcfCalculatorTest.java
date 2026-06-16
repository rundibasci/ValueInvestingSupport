package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class DcfCalculatorTest {

    private DcfCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DcfCalculator();
    }

    // Base input: 10% growth y1-5, 5% y6-10, 2% terminal, 9% WACC, shares=10, netDebt=0
    private DcfInput baseInput(int fcfYearsPositive) {
        return new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                fcfYearsPositive
        );
    }

    @Test
    void rule06_returnsEmpty_whenFcfYearsPositiveLessThan3() {
        assertThat(calculator.calculate(baseInput(2))).isEmpty();
        assertThat(calculator.calculate(baseInput(0))).isEmpty();
        assertThat(calculator.calculate(baseInput(1))).isEmpty();
    }

    @Test
    void rule06_returnsResult_whenFcfYearsPositiveEqualsExactly3() {
        assertThat(calculator.calculate(baseInput(3))).isPresent();
    }

    @Test
    void rule06_doesNotThrow_returnsEmpty() {
        Optional<DcfResult> result = calculator.calculate(baseInput(2));
        assertThat(result).isEmpty();
    }

    @Test
    void fairValueOrdering_higherWaccProducesLowerValue() {
        // fairValueLow uses WACC+2%, fairValueHigh uses WACC-1%
        Optional<DcfResult> result = calculator.calculate(baseInput(5));
        assertThat(result).isPresent();
        DcfResult r = result.get();
        assertThat(r.fairValueLow().compareTo(r.fairValue())).isLessThan(0);
        assertThat(r.fairValueHigh().compareTo(r.fairValue())).isGreaterThan(0);
    }

    @Test
    void fairValueIsPositiveForPositiveInputs() {
        Optional<DcfResult> result = calculator.calculate(baseInput(5));
        assertThat(result).isPresent();
        assertThat(result.get().fairValue().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        assertThat(result.get().enterpriseValue().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void fairValueHasScale2() {
        Optional<DcfResult> result = calculator.calculate(baseInput(5));
        assertThat(result).isPresent();
        DcfResult r = result.get();
        assertThat(r.fairValue().scale()).isEqualTo(2);
        assertThat(r.fairValueLow().scale()).isEqualTo(2);
        assertThat(r.fairValueHigh().scale()).isEqualTo(2);
        assertThat(r.enterpriseValue().scale()).isEqualTo(2);
    }

    @Test
    void inputParametersPreservedInResult() {
        DcfInput input = baseInput(5);
        Optional<DcfResult> result = calculator.calculate(input);
        assertThat(result).isPresent();
        assertThat(result.get().parameters()).isSameAs(input);
    }

    @Test
    void netDebtReducesFairValue() {
        DcfInput withDebt = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                new BigDecimal("5000"),
                5
        );
        DcfResult noDebt = calculator.calculate(baseInput(5)).get();
        DcfResult withDebtResult = calculator.calculate(withDebt).get();
        assertThat(withDebtResult.fairValue().compareTo(noDebt.fairValue())).isLessThan(0);
    }

    @Test
    void netCashPosition_increasesFairValue() {
        // negative netDebt = net cash on balance sheet → equity value is higher
        DcfInput netCash = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                new BigDecimal("-5000"),
                5
        );
        DcfResult noDebt = calculator.calculate(baseInput(5)).get();
        DcfResult netCashResult = calculator.calculate(netCash).get();
        assertThat(netCashResult.fairValue().compareTo(noDebt.fairValue())).isGreaterThan(0);
    }

    @Test
    void higherFcfProducesHigherFairValue() {
        DcfInput doubleFcf = new DcfInput(
                new BigDecimal("2000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                5
        );
        DcfResult base = calculator.calculate(baseInput(5)).get();
        DcfResult doubled = calculator.calculate(doubleFcf).get();
        assertThat(doubled.fairValue().compareTo(base.fairValue())).isGreaterThan(0);
    }

    @Test
    void moreSharesProducesLowerFairValuePerShare() {
        DcfInput moreShares = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                5
        );
        DcfResult base = calculator.calculate(baseInput(5)).get();
        DcfResult diluted = calculator.calculate(moreShares).get();
        assertThat(diluted.fairValue().compareTo(base.fairValue())).isLessThan(0);
    }

    @Test
    void exactBoundary3_fairValueIsPositive() {
        DcfResult result = calculator.calculate(baseInput(3)).get();
        assertThat(result.fairValue().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
        assertThat(result.enterpriseValue().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    void referenceValue_zeroGrowthPerpetuity_matchesCoverR() {
        // With zero growth and zero terminal rate, the 10-year DCF + perpetuity terminal
        // equals the perpetuity formula C/r by mathematical identity (errors < 1 cent).
        DcfInput input = new DcfInput(
                new BigDecimal("100"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.10"),
                BigDecimal.ONE,
                BigDecimal.ZERO,
                5
        );
        DcfResult result = calculator.calculate(input).get();
        // base: 100/0.10 = 1000.00
        assertThat(result.fairValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(result.enterpriseValue()).isEqualByComparingTo(new BigDecimal("1000.00"));
        // low (WACC+2% = 0.12): 100/0.12 = 833.33...
        assertThat(result.fairValueLow()).isCloseTo(new BigDecimal("833.33"), offset(new BigDecimal("0.02")));
        // high (WACC-1% = 0.09): 100/0.09 = 1111.11...
        assertThat(result.fairValueHigh()).isCloseTo(new BigDecimal("1111.11"), offset(new BigDecimal("0.02")));
    }

    @Test
    void terminalRateEqualToWacc_throwsIllegalArgumentException() {
        DcfInput input = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.09"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                5
        );
        assertThatThrownBy(() -> calculator.calculate(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalRateGreaterThanWacc_throwsIllegalArgumentException() {
        DcfInput input = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.10"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                5
        );
        assertThatThrownBy(() -> calculator.calculate(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enterpriseValueUnaffectedBySharesAndDebt() {
        // EV is a function of FCF + growth + WACC only; shares and netDebt only affect per-share FV
        DcfInput withDebt = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                new BigDecimal("5000"),
                5
        );
        DcfInput moreShares = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.05"),
                new BigDecimal("0.02"),
                new BigDecimal("0.09"),
                new BigDecimal("100"),
                BigDecimal.ZERO,
                5
        );
        BigDecimal baseEV = calculator.calculate(baseInput(5)).get().enterpriseValue();
        BigDecimal debtEV = calculator.calculate(withDebt).get().enterpriseValue();
        BigDecimal sharesEV = calculator.calculate(moreShares).get().enterpriseValue();

        assertThat(baseEV).isEqualByComparingTo(debtEV);
        assertThat(baseEV).isEqualByComparingTo(sharesEV);
    }
}
