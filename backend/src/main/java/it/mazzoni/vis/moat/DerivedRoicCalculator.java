package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class DerivedRoicCalculator {
    static final BigDecimal CONSERVATIVE_TAX_PROXY = new BigDecimal("0.25");
    static final String FORMULA = "NOPAT = EBIT × (1 − tax rate); invested capital = equity + debt − cash; ROIC = NOPAT ÷ average opening/closing invested capital.";

    public Calculation calculate(FundamentalSnapshot current, FundamentalSnapshot prior) {
        if (current.getOperatingIncome() == null) return Calculation.unavailable("MISSING_EBIT");
        BigDecimal closingCapital = investedCapital(current);
        if (closingCapital == null) return Calculation.unavailable("MISSING_INVESTED_CAPITAL_INPUTS");
        if (closingCapital.signum() <= 0) return Calculation.unavailable("NON_POSITIVE_INVESTED_CAPITAL");
        if (prior == null) return Calculation.unavailable("MISSING_OPENING_INVESTED_CAPITAL");
        BigDecimal openingCapital = investedCapital(prior);
        if (openingCapital == null) return Calculation.unavailable("MISSING_OPENING_INVESTED_CAPITAL");
        if (openingCapital.signum() <= 0) return Calculation.unavailable("NON_POSITIVE_OPENING_INVESTED_CAPITAL");

        TaxRate tax = taxRate(current);
        BigDecimal nopat = current.getOperatingIncome().multiply(BigDecimal.ONE.subtract(tax.value()));
        BigDecimal averageCapital = openingCapital.add(closingCapital)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        BigDecimal roic = nopat.divide(averageCapital, 8, RoundingMode.HALF_UP);
        String note = FORMULA + " Tax rate: " + tax.note();
        return new Calculation(roic, note, null);
    }

    private BigDecimal investedCapital(FundamentalSnapshot snapshot) {
        if (snapshot.getTotalEquity() == null || snapshot.getTotalDebt() == null || snapshot.getCash() == null) {
            return null;
        }
        return snapshot.getTotalEquity().add(snapshot.getTotalDebt()).subtract(snapshot.getCash());
    }

    private TaxRate taxRate(FundamentalSnapshot snapshot) {
        if (snapshot.getPretaxIncome() != null && snapshot.getIncomeTaxExpense() != null
                && snapshot.getPretaxIncome().signum() > 0) {
            BigDecimal rate = snapshot.getIncomeTaxExpense()
                    .divide(snapshot.getPretaxIncome(), 8, RoundingMode.HALF_UP);
            if (rate.signum() >= 0 && rate.compareTo(new BigDecimal("0.50")) <= 0) {
                return new TaxRate(rate, "reported effective tax rate");
            }
        }
        return new TaxRate(CONSERVATIVE_TAX_PROXY, "25% conservative proxy (reported rate unavailable or invalid)");
    }

    public record Calculation(BigDecimal roic, String formulaNote, String unavailableReason) {
        static Calculation unavailable(String reason) {
            return new Calculation(null, FORMULA, reason);
        }

        public boolean available() { return roic != null; }
    }

    private record TaxRate(BigDecimal value, String note) {}
}
