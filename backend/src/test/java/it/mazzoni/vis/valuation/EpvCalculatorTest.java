package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpvCalculatorTest {

    @Test
    void rule08SkipsWhenFewerThanFiveYears() {
        EpvInput input = new EpvInput(
                List.of(new BigDecimal("100"), new BigDecimal("110")),
                new BigDecimal("0.10"),
                BigDecimal.ZERO,
                BigDecimal.TEN);

        assertThat(new EpvCalculator().calculate(input)).isEmpty();
    }

    @Test
    void calculatesZeroGrowthPerShareFloorFromAverageEarnings() {
        EpvInput input = new EpvInput(
                List.of(new BigDecimal("100"), new BigDecimal("120"), new BigDecimal("140"),
                        new BigDecimal("160"), new BigDecimal("180")),
                new BigDecimal("0.10"),
                new BigDecimal("200"),
                BigDecimal.TEN);

        EpvResult result = new EpvCalculator().calculate(input).orElseThrow();

        assertThat(result.normalizedEarnings()).isEqualByComparingTo("140.00");
        assertThat(result.fairValue()).isEqualByComparingTo("120.00");
        assertThat(result.yearsAveraged()).isEqualTo(5);
    }
}
