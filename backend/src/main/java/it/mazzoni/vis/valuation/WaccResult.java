package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record WaccResult(
        BigDecimal wacc,
        BigDecimal riskFreeRate,
        BigDecimal equityRiskPremium,
        BigDecimal beta,
        BigDecimal costOfEquity,
        BigDecimal costOfDebt,
        BigDecimal debtWeight,
        BigDecimal equityWeight,
        BigDecimal effectiveTaxRate,
        boolean fallbackUsed,
        String source
) {}
