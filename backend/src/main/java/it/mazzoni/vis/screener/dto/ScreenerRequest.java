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
                altmanZone, null, null, sortField, sortDirection, page, pageSize);
    }
}
