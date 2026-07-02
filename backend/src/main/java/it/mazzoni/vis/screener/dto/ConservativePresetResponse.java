package it.mazzoni.vis.screener.dto;

import java.util.List;

public record ConservativePresetResponse(
        String name,
        String description,
        ScreenerRequest criteria,
        List<ConservativeCriterionResponse> criteriaSummary,
        String decisionSupportNote
) {
}
