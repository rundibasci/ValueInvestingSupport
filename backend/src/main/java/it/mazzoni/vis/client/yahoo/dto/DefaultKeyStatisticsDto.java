package it.mazzoni.vis.client.yahoo.dto;

public record DefaultKeyStatisticsDto(
        YahooValue enterpriseValue,
        YahooValue forwardPE,
        YahooValue profitMargins,
        YahooValue sharesOutstanding,
        YahooValue bookValue,
        YahooValue priceToBook,
        YahooValue trailingEps,
        YahooValue forwardEps,
        YahooValue lastDividendValue
) {}
