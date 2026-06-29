package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record WaccInput(
        BigDecimal riskFreeRate,
        BigDecimal equityRiskPremium,
        BigDecimal beta,
        BigDecimal costOfDebt,
        BigDecimal debt,
        BigDecimal equity,
        BigDecimal effectiveTaxRate,
        BigDecimal fallbackWacc
) {}
