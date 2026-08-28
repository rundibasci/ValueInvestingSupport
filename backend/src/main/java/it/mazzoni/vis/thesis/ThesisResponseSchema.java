package it.mazzoni.vis.thesis;

import com.google.genai.types.Schema;

import java.util.List;

/**
 * Builds the Vertex AI {@code responseSchema} for the thesis-generation call — a Java
 * reconstruction of the exact structure vis-model-training's Python
 * {@code vis_training.vertex.schema_adapter.to_vertex_response_schema()} derives from
 * thesis-output.schema.json (see config/vertex-gemini-v1.json's checked-in
 * {@code generationConfig.responseSchema}).
 *
 * <p>Deliberately hand-written to match, not generated from the JSON at runtime — two
 * independently-verified paths on two separate deployable artifacts (Python training repo,
 * Java backend). {@code ThesisConfigParityTest} is the mechanism that catches drift between
 * this and the checked-in Python-side config, not a promise drift cannot happen (see
 * requirements.md -> Compatibility and Risks).
 *
 * <p>Two Vertex-specific incompatibilities TA3 found via live smoke tests, both already
 * reflected here: no {@code uniqueItems} field exists on {@link Schema} at all (confirmed by
 * javap against the real google-genai-1.68.0 jar), and every bare {@code enum} node needs an
 * explicit {@code type} (TA3's "response schemas didn't specify the schema type field" 400).
 */
final class ThesisResponseSchema {

    private ThesisResponseSchema() {}

    private static final List<String> EVIDENCE_FIELDS = List.of(
            "marketPrice", "intrinsicValue", "marginOfSafetyPercent", "valueScore",
            "dividendYieldPercent", "payoutRatioPercent", "netDebtToEbitda",
            "revenueTrend", "earningsTrend", "freeCashFlowTrend", "dataQuality", "deterministicWarnings"
    );

    static Schema build() {
        Schema evidenceField = Schema.builder()
                .type("STRING")
                .enum_(EVIDENCE_FIELDS)
                .build();

        Schema evidenceItem = Schema.builder()
                .type("OBJECT")
                .required("claim", "evidenceFields")
                .properties(java.util.Map.of(
                        "claim", Schema.builder().type("STRING").minLength(1L).build(),
                        "evidenceFields", Schema.builder()
                                .type("ARRAY")
                                .minItems(1L)
                                .items(evidenceField)
                                .build()
                ))
                .build();

        Schema evidenceArray = Schema.builder().type("ARRAY").items(evidenceItem).build();
        Schema stringArray = Schema.builder().type("ARRAY").items(Schema.builder().type("STRING").build()).build();

        return Schema.builder()
                .type("OBJECT")
                .required(
                        "classification", "confidence", "summary", "bullCase", "bearCase",
                        "keyRisks", "keyAssumptions", "invalidationConditions", "dataWarnings",
                        "humanReviewRequired"
                )
                .properties(java.util.Map.of(
                        "classification", Schema.builder().type("STRING").enum_(
                                "POTENTIALLY_UNDERVALUED", "FAIRLY_VALUED", "POTENTIALLY_OVERVALUED",
                                "UNDER_REVIEW", "INSUFFICIENT_DATA"
                        ).build(),
                        "confidence", Schema.builder().type("NUMBER").minimum(0.0).maximum(1.0).build(),
                        "summary", Schema.builder().type("STRING").minLength(1L).maxLength(1000L).build(),
                        "bullCase", evidenceArray,
                        "bearCase", evidenceArray,
                        "keyRisks", stringArray,
                        "keyAssumptions", stringArray,
                        "invalidationConditions", stringArray,
                        "dataWarnings", stringArray,
                        "humanReviewRequired", Schema.builder().type("BOOLEAN").build()
                ))
                .build();
    }
}
