package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PortfolioAnalyticsResponse(
        UUID portfolioId,
        BigDecimal totalMarketValue,
        WeightedMetricsResponse weightedMetrics,
        Map<String, BigDecimal> sectorWeights,
        List<String> sectorConcentrationFlags,
        List<HoldingConcentrationResponse> holdingConcentration,
        MoatProfileResponse moatProfile,
        QualityDistributionResponse qualityDistribution,
        List<LiquidityResult> liquidity,
        BenchmarkComparisonResponse benchmarkComparison,
        List<AnalyticsWarning> warnings,
        UUID snapshotId,
        LocalDateTime capturedAt
) {
}
