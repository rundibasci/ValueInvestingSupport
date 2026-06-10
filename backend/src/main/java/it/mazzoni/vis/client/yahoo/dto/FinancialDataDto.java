package it.mazzoni.vis.client.yahoo.dto;

public record FinancialDataDto(
        YahooValue currentPrice,
        YahooValue totalCash,
        YahooValue totalDebt,
        YahooValue totalRevenue,
        YahooValue grossProfit,
        YahooValue ebitda,
        YahooValue operatingCashflow,
        YahooValue freeCashflow,
        YahooValue returnOnAssets,
        YahooValue returnOnEquity,
        YahooValue debtToEquity,
        YahooValue currentRatio,
        YahooValue quickRatio
) {}
