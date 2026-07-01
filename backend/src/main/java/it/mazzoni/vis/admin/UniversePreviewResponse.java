package it.mazzoni.vis.admin;

import java.util.List;

public record UniversePreviewResponse(
        int totalMatches,
        int returnedCount,
        boolean capped,
        String warning,
        List<UniversePreviewRow> symbols
) {}
