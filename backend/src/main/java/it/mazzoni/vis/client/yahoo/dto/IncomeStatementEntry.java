package it.mazzoni.vis.client.yahoo.dto;

public record IncomeStatementEntry(
        YahooValue endDate,
        YahooValue totalRevenue,
        YahooValue grossProfit,
        YahooValue netIncome,
        YahooValue ebit
) {}
