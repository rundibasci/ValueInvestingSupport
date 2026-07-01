package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record WeightedMetricsResponse(
        BigDecimal marginOfSafety,
        BigDecimal peRatio,
        BigDecimal dividendYield,
        BigDecimal valueScore,
        BigDecimal piotroskiFScore
) {
}
