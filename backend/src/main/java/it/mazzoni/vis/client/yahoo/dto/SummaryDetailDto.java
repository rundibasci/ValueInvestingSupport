package it.mazzoni.vis.client.yahoo.dto;

public record SummaryDetailDto(
        YahooValue trailingPE,
        YahooValue forwardPE,
        YahooValue dividendRate,
        YahooValue dividendYield,
        YahooValue payoutRatio,
        YahooValue beta,
        YahooValue marketCap,
        YahooValue fiftyTwoWeekLow,
        YahooValue fiftyTwoWeekHigh,
        String currency
) {}
