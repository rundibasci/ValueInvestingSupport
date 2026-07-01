package it.mazzoni.vis.admin;

import java.util.List;

public record UniverseSeedCriteriaResponse(
        UniversePreviewResponse preview,
        List<SeedResult> results
) {}
