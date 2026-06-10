package it.mazzoni.vis.client.yahoo.dto;

public record AssetProfileDto(
        String sector,
        String industry,
        String country,
        String longBusinessSummary,
        Integer fullTimeEmployees
) {}
