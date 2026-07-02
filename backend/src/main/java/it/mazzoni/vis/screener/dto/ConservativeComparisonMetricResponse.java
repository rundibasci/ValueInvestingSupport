package it.mazzoni.vis.screener.dto;

public record ConservativeComparisonMetricResponse(
        String group,
        String label,
        String value,
        String availabilityStatus,
        String coverageNote
) {
}
