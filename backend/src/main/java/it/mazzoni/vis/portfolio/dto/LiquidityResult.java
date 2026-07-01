package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record LiquidityResult(
        String symbol,
        BigDecimal averageDailyDollarVolume,
        BigDecimal daysToLiquidate,
        String classification,
        String availabilityStatus
) {
}
