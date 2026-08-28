package it.mazzoni.vis.thesis;

/** Adapts TRAIN-12's runtime-contract interface (vis-model-training/README.md §12.2) to a
 * pinned Gemini engine. Default backend tests mock this interface and never call live
 * Vertex AI (specs/tech-stack.md's test-isolation rule). */
public interface InvestmentThesisClient {
    ThesisGenerationResult generate(ThesisGenerationRequest request);
}
