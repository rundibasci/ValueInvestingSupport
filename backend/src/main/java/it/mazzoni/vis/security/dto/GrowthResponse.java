package it.mazzoni.vis.security.dto;

public record GrowthResponse(
        String symbol,
        GrowthMetrics revenue,
        GrowthMetrics fcf,
        GrowthMetrics eps
) {}
