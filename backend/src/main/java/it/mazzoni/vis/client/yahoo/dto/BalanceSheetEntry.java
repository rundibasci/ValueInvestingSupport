package it.mazzoni.vis.client.yahoo.dto;

public record BalanceSheetEntry(
        YahooValue endDate,
        YahooValue cash,
        YahooValue totalCurrentAssets,
        YahooValue totalAssets,
        YahooValue totalCurrentLiabilities,
        YahooValue totalLiab,
        YahooValue longTermDebt,
        YahooValue totalStockholderEquity,
        YahooValue netTangibleAssets
) {}
