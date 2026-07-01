package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.util.Map;

public record BenchmarkComparisonResponse(
        String benchmarkSymbol,
        BigDecimal portfolioPeRatio,
        BigDecimal benchmarkPeRatio,
        BigDecimal portfolioDividendYield,
        BigDecimal benchmarkDividendYield,
        BigDecimal portfolioMarginOfSafety,
        BigDecimal benchmarkMarginOfSafety,
        Map<String, BigDecimal> sectorWeightDifference,
        String availabilityStatus
) {
}
