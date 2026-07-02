package it.mazzoni.vis.screener.dto;

import java.util.List;

public record ConservativeComparisonRowResponse(
        String symbol,
        String companyName,
        List<ConservativeComparisonMetricResponse> metrics
) {
}
