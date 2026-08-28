package it.mazzoni.vis.thesis;

/**
 * Adapts TRAIN-12.2's error-code table (vis-model-training/README.md §12.2) to a
 * single-pinned-model engine. {@code MODEL_VERSION_UNAVAILABLE} (an adapter-promotion-registry
 * concept: CANDIDATE/APPROVED/DEPRECATED) has no runtime equivalent here — a blank/unset
 * {@code GEMINI_MODEL_ID} is a startup-time configuration failure (see
 * {@code ThesisProperties}), not a code returned at request time.
 * {@code HUMAN_REVIEW_REQUIRED} is deliberately not a member of this enum: per TRAIN-12.2 it
 * is not an error, it is a valid {@code ThesisGenerationSuccess} whose
 * {@code output().humanReviewRequired()} is true.
 */
public enum ThesisErrorCode {
    /** Output not parseable or non-conforming after the allowed retries. */
    SCHEMA_VALIDATION_FAILED,
    /** Configured timeout exceeded. */
    TIMEOUT,
    /** The supplied {@link ThesisInput} does not conform to thesis-input.schema.json — rejected before any Gemini call, never retried. */
    INPUT_SCHEMA_INVALID
}
