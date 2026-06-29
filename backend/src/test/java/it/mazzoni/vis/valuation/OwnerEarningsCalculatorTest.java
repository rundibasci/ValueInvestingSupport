package it.mazzoni.vis.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OwnerEarningsCalculatorTest {

    @Test
    void calculatesOwnerEarningsNetOfMaintenanceCapex() {
        OwnerEarningsCalculator calculator = new OwnerEarningsCalculator();
        BigDecimal maintenanceCapex = calculator.estimateMaintenanceCapex(
                new BigDecimal("100"), new BigDecimal("0.70"));

        assertThat(maintenanceCapex).isEqualByComparingTo("70.00");
        assertThat(calculator.calculate(new BigDecimal("500"), new BigDecimal("100"), maintenanceCapex))
                .isEqualByComparingTo("530.00");
    }
}
