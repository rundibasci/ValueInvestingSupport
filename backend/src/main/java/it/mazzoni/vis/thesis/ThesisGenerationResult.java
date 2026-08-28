package it.mazzoni.vis.thesis;

import java.util.UUID;

/** Adapts TRAIN-12.2's response envelope (OK / ERROR). */
public sealed interface ThesisGenerationResult permits ThesisGenerationResult.Success, ThesisGenerationResult.Failure {

    UUID requestId();

    record Success(UUID requestId, String modelId, String modelVersion, String promptVersion,
                    int latencyMs, ThesisOutput output) implements ThesisGenerationResult {}

    /** {@code rawOutputAvailable} indicates only whether the raw (non-conforming) output was
     * retained for audit (TRAIN-12.5) — never itself returned as a substitute for a valid output. */
    record Failure(UUID requestId, ThesisErrorCode errorCode, String errorMessage,
                    boolean rawOutputAvailable) implements ThesisGenerationResult {}
}
