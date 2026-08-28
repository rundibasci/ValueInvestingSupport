package it.mazzoni.vis.thesis;

import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

/**
 * Thin seam around {@code com.google.genai.Client.models.generateContent(...)} so
 * {@link VertexAiInvestmentThesisClient} can be unit-tested with an injected fake — the same
 * {@code client_factory} discipline TA3's Python {@code VertexBackend} already established
 * (zero network calls, zero real credentials in the default test suite).
 */
public interface GeminiCaller {
    GenerateContentResponse call(String model, String userContent, GenerateContentConfig config);
}
