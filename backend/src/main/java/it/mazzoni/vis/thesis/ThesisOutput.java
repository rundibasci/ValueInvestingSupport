package it.mazzoni.vis.thesis;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mirrors vis-model-training/schemas/thesis-output.schema.json exactly — either Gemini's
 * actual structured response, or the deterministic-fallback synthetic body (TRAIN-12.3:
 * {@code classification=UNDER_REVIEW}, {@code humanReviewRequired=true}, empty bull/bear
 * case, the tracked error reason in {@code dataWarnings}).
 */
public record ThesisOutput(
        ThesisClassification classification,
        BigDecimal confidence,
        String summary,
        List<EvidenceClaim> bullCase,
        List<EvidenceClaim> bearCase,
        List<String> keyRisks,
        List<String> keyAssumptions,
        List<String> invalidationConditions,
        List<String> dataWarnings,
        boolean humanReviewRequired
) {
    public static ThesisOutput deterministicFallback(String errorReason) {
        return new ThesisOutput(
                ThesisClassification.UNDER_REVIEW,
                null,
                "Generation failed; this is a deterministic fallback, not a model-generated thesis.",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(errorReason),
                true
        );
    }
}
