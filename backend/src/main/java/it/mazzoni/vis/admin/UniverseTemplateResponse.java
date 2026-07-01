package it.mazzoni.vis.admin;

public record UniverseTemplateResponse(
        String id,
        String name,
        String description,
        UniverseSelectionRequest criteria
) {}
