package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DcfSensitivityServiceTest {

    @Test
    void returnsThreeByThreeMatrixForValidBaseInput() {
        DcfInput input = new DcfInput(
                new BigDecimal("1000"),
                new BigDecimal("0.08"),
                new BigDecimal("0.04"),
                new BigDecimal("0.025"),
                new BigDecimal("0.09"),
                new BigDecimal("10"),
                BigDecimal.ZERO,
                5);

        DcfSensitivityResult result = new DcfSensitivityService().analyze(input);

        assertThat(result.waccValues()).hasSize(3);
        assertThat(result.terminalRateValues()).hasSize(3);
        assertThat(result.cells()).hasSize(9);
        assertThat(result.cells()).allMatch(cell -> cell.fairValue().compareTo(BigDecimal.ZERO) > 0);
    }
}
