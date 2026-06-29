package it.mazzoni.vis.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class WaccCalculator {

    public WaccResult compute(WaccInput input) {
        if (input.beta() == null
                || input.debt() == null
                || input.equity() == null
                || input.debt().add(input.equity()).compareTo(BigDecimal.ZERO) <= 0) {
            return fallback(input);
        }

        BigDecimal riskFreeRate = input.riskFreeRate();
        BigDecimal equityRiskPremium = input.equityRiskPremium();
        BigDecimal costOfEquity = riskFreeRate.add(input.beta().multiply(equityRiskPremium));
        BigDecimal totalCapital = input.debt().add(input.equity());
        BigDecimal debtWeight = input.debt().divide(totalCapital, 6, RoundingMode.HALF_UP);
        BigDecimal equityWeight = input.equity().divide(totalCapital, 6, RoundingMode.HALF_UP);
        BigDecimal costOfDebt = input.costOfDebt() != null ? input.costOfDebt() : riskFreeRate;
        BigDecimal taxRate = input.effectiveTaxRate() != null ? input.effectiveTaxRate() : BigDecimal.ZERO;
        BigDecimal afterTaxCostOfDebt = costOfDebt.multiply(BigDecimal.ONE.subtract(taxRate));
        BigDecimal wacc = equityWeight.multiply(costOfEquity)
                .add(debtWeight.multiply(afterTaxCostOfDebt))
                .setScale(6, RoundingMode.HALF_UP);

        return new WaccResult(
                wacc,
                riskFreeRate,
                equityRiskPremium,
                input.beta(),
                costOfEquity.setScale(6, RoundingMode.HALF_UP),
                costOfDebt,
                debtWeight,
                equityWeight,
                taxRate,
                false,
                "computed");
    }

    private WaccResult fallback(WaccInput input) {
        BigDecimal fallback = input.fallbackWacc();
        return new WaccResult(
                fallback,
                input.riskFreeRate(),
                input.equityRiskPremium(),
                input.beta(),
                null,
                input.costOfDebt(),
                null,
                null,
                input.effectiveTaxRate(),
                true,
                "sector-fallback");
    }
}
