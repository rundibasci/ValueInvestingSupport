package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record ValuationParams(
        BigDecimal wacc,
        BigDecimal growthY1Y5,
        BigDecimal growthY6Y10,
        BigDecimal terminalRate,
        BigDecimal requiredReturn,
        BigDecimal dividendGrowthRate
) {}
