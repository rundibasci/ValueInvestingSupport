package it.mazzoni.vis.screener.dto;

public record ConservativeCriterionResponse(
        String key,
        String label,
        String currentValue,
        String whyItMatters,
        String relaxation
) {
}
