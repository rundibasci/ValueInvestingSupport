package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record EpvResult(
        BigDecimal fairValue,
        BigDecimal normalizedEarnings,
        int yearsAveraged
) {}
