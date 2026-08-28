package it.mazzoni.vis.thesis.dto;

import it.mazzoni.vis.thesis.ThesisOutput;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * GET /api/v1/securities/{symbol}/thesis — the latest persisted thesis for a symbol.
 * {@code stale=true} when the underlying ValuationResult/ValueScore was refreshed after
 * {@code generatedAt} (specs/tech-stack.md's investment_thesis_result data-model note).
 */
public record ThesisResponse(UUID id, String symbol, String status, String modelId, String modelVersion,
                             String promptVersion, ThesisOutput output, LocalDateTime generatedAt,
                             boolean stale) {

    public static ThesisResponse notGenerated(String symbol) {
        return new ThesisResponse(null, symbol, "NOT_GENERATED", null, null, null, null, null, false);
    }
}
