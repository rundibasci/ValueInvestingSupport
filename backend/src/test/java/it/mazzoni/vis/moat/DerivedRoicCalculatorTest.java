package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DerivedRoicCalculatorTest {
    private final DerivedRoicCalculator calculator = new DerivedRoicCalculator();

    @Test
    void derivesRoicFromNopatAndAverageInvestedCapital() {
        FundamentalSnapshot current = snapshot("100", "250", "100", "50");
        current.setPretaxIncome(new BigDecimal("80"));
        current.setIncomeTaxExpense(new BigDecimal("16"));
        FundamentalSnapshot prior = snapshot("90", "210", "90", "40");

        var result = calculator.calculate(current, prior);

        assertThat(result.roic()).isEqualByComparingTo("0.28571429");
        assertThat(result.formulaNote()).contains("reported effective tax rate");
        assertThat(result.unavailableReason()).isNull();
    }

    @Test
    void usesDocumentedProxyWhenReportedTaxRateIsUnavailable() {
        var result = calculator.calculate(snapshot("100", "250", "100", "50"),
                snapshot("90", "210", "90", "40"));

        assertThat(result.roic()).isEqualByComparingTo("0.26785714");
        assertThat(result.formulaNote()).contains("25% conservative proxy");
    }

    @Test
    void refusesToFabricateAValueWithoutOpeningCapital() {
        var result = calculator.calculate(snapshot("100", "250", "100", "50"), null);

        assertThat(result.roic()).isNull();
        assertThat(result.unavailableReason()).isEqualTo("MISSING_OPENING_INVESTED_CAPITAL");
    }

    private FundamentalSnapshot snapshot(String ebit, String equity, String debt, String cash) {
        FundamentalSnapshot snapshot = new FundamentalSnapshot();
        snapshot.setOperatingIncome(new BigDecimal(ebit));
        snapshot.setTotalEquity(new BigDecimal(equity));
        snapshot.setTotalDebt(new BigDecimal(debt));
        snapshot.setCash(new BigDecimal(cash));
        return snapshot;
    }
}
