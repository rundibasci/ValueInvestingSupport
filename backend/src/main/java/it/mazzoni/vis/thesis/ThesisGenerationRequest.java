package it.mazzoni.vis.thesis;

import java.util.UUID;

/** Adapts TRAIN-12.2's request envelope. */
public record ThesisGenerationRequest(UUID requestId, String modelVersion, ThesisInput input) {}
