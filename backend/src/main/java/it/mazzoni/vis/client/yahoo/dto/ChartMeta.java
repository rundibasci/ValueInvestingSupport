package it.mazzoni.vis.client.yahoo.dto;

public record ChartMeta(
        String symbol,
        String currency,
        Double regularMarketPrice,
        String longName,
        String shortName
) {}
