package it.mazzoni.vis.screener.dto;

import java.math.BigDecimal;

public record ScreenerRequest(
        String sector,
        String exchange,
        BigDecimal minMarginOfSafety,
        BigDecimal maxMarginOfSafety,
        BigDecimal minValueScore,
        BigDecimal minRoic,
        BigDecimal maxDebtToEquity,
        BigDecimal minDividendYield,
        BigDecimal minRevenueGrowth,
        Integer piotroskiMin,
        Integer piotroskiMax,
        String altmanZone,
        String moatStrength,
        String sharesOutstandingTrend,
        BigDecimal maxPriceToFfo,
        BigDecimal maxNetDebtToEbitda,
        BigDecimal maxAffoPayoutRatio,
        String sortField,
        String sortDirection,
        Integer page,
        Integer pageSize
) {
    public ScreenerRequest(
            String sector,
            String exchange,
            BigDecimal minMarginOfSafety,
            BigDecimal maxMarginOfSafety,
            BigDecimal minValueScore,
            BigDecimal minRoic,
            BigDecimal maxDebtToEquity,
            BigDecimal minDividendYield,
            BigDecimal minRevenueGrowth,
            Integer piotroskiMin,
            Integer piotroskiMax,
            String altmanZone,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        this(sector, exchange, minMarginOfSafety, maxMarginOfSafety, minValueScore, minRoic,
                maxDebtToEquity, minDividendYield, minRevenueGrowth, piotroskiMin, piotroskiMax,
                altmanZone, null, null, null, null, null, sortField, sortDirection, page, pageSize);
    }

    // RM3 (specs/2026-09-02-rm3-screener-security-detail-surfacing/): backward-compatible shape
    // for callers/tests built against the pre-RM3 18-arg constructor (sector..altmanZone,
    // moatStrength, sharesOutstandingTrend, sortField..pageSize) — the 3 new REIT-only filters
    // default to null (no filtering).
    public ScreenerRequest(
            String sector,
            String exchange,
            BigDecimal minMarginOfSafety,
            BigDecimal maxMarginOfSafety,
            BigDecimal minValueScore,
            BigDecimal minRoic,
            BigDecimal maxDebtToEquity,
            BigDecimal minDividendYield,
            BigDecimal minRevenueGrowth,
            Integer piotroskiMin,
            Integer piotroskiMax,
            String altmanZone,
            String moatStrength,
            String sharesOutstandingTrend,
            String sortField,
            String sortDirection,
            Integer page,
            Integer pageSize
    ) {
        this(sector, exchange, minMarginOfSafety, maxMarginOfSafety, minValueScore, minRoic,
                maxDebtToEquity, minDividendYield, minRevenueGrowth, piotroskiMin, piotroskiMax,
                altmanZone, moatStrength, sharesOutstandingTrend, null, null, null,
                sortField, sortDirection, page, pageSize);
    }
}
