package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.scoring.dto.ValueScoreResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SecurityReviewResponse(
        String symbol,
        SecurityDetailResponse detail,
        FinancialsResponse financials,
        RatiosHistoryResponse ratios,
        ValuationDetailResponse valuation,
        DividendsResponse dividends,
        GrowthResponse growth,
        PeersResponse peers,
        ValueScoreResponse score,
        FinancialHealth financialHealth,
        List<SourceCoverageItem> sourceCoverage,
        List<FreshnessItem> freshness,
        List<AvailabilityItem> availability,
        List<DataQualityNote> dataQualityNotes
) {
    public record FinancialHealth(
            BigDecimal totalDebt,
            BigDecimal cash,
            BigDecimal netDebt,
            BigDecimal debtToEquity,
            BigDecimal currentRatio,
            BigDecimal quickRatio,
            BigDecimal interestCoverage,
            BigDecimal payoutRatio,
            BigDecimal dividendYield,
            BigDecimal grossMargin,
            BigDecimal operatingMargin,
            BigDecimal netMargin,
            LocalDate dataAsOf
    ) {}

    public record SourceCoverageItem(
            String category,
            String provider,
            String status,
            String message
    ) {}

    public record FreshnessItem(
            String category,
            LocalDate dataAsOf,
            String status,
            String message
    ) {}

    public record AvailabilityItem(
            String category,
            AvailabilityResponse state
    ) {}

    public record DataQualityNote(
            String category,
            String severity,
            String message
    ) {}
}
