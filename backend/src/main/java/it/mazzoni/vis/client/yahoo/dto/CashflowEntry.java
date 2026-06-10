package it.mazzoni.vis.client.yahoo.dto;

public record CashflowEntry(
        YahooValue endDate,
        YahooValue netIncome,
        YahooValue totalCashFromOperatingActivities,
        YahooValue capitalExpenditures,
        YahooValue dividendsPaid
) {}
