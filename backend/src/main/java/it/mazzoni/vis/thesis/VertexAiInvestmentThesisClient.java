package it.mazzoni.vis.thesis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.ThinkingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Adapts TRAIN-12's runtime contract to Vertex AI Gemini. Loads decoding config from
 * {@link ThesisProperties} (never hardcoded, and pinned to match
 * vis-model-training/config/vertex-gemini-v1.json exactly — verified by
 * ThesisConfigParityTest). No grounding tools are ever configured (defense-in-depth,
 * mirroring TA3's Python {@code VertexBackend}: this integration reasons only over
 * VIS-supplied context, per ADR-002/mission.md).
 *
 * <p>Retries only {@link ThesisErrorCode#SCHEMA_VALIDATION_FAILED} and
 * {@link ThesisErrorCode#TIMEOUT} — never to "regenerate a more convincing output"
 * (TRAIN-12.3, unchanged). {@link ThesisErrorCode#INPUT_SCHEMA_INVALID} short-circuits with
 * no retry and no Gemini call at all.
 */
public class VertexAiInvestmentThesisClient implements InvestmentThesisClient {

    private static final Logger log = LoggerFactory.getLogger(VertexAiInvestmentThesisClient.class);

    private final ThesisProperties properties;
    private final GeminiCaller caller;
    private final ThesisPromptLoader promptLoader;
    private final ObjectMapper objectMapper;

    public VertexAiInvestmentThesisClient(ThesisProperties properties, GeminiCaller caller, ThesisPromptLoader promptLoader) {
        this.properties = properties;
        this.caller = caller;
        this.promptLoader = promptLoader;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public ThesisGenerationResult generate(ThesisGenerationRequest request) {
        String validationError = validate(request.input());
        if (validationError != null) {
            return new ThesisGenerationResult.Failure(request.requestId(), ThesisErrorCode.INPUT_SCHEMA_INVALID,
                    validationError, false);
        }

        String userContent;
        try {
            userContent = objectMapper.writeValueAsString(request.input());
        } catch (Exception e) {
            // Our own serialization failing is a programming error, not a runtime data
            // problem -- still routed through the same envelope, never an unhandled exception.
            return new ThesisGenerationResult.Failure(request.requestId(), ThesisErrorCode.INPUT_SCHEMA_INVALID,
                    "Failed to serialize input: " + e.getMessage(), false);
        }

        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(com.google.genai.types.Part.fromText(promptLoader.promptText())))
                .temperature(ThesisProperties.TEMPERATURE)
                .maxOutputTokens(ThesisProperties.MAX_OUTPUT_TOKENS)
                .responseMimeType("application/json")
                .responseSchema(ThesisResponseSchema.build())
                .thinkingConfig(ThinkingConfig.builder().thinkingBudget(ThesisProperties.THINKING_BUDGET))
                .build();

        int attempt = 0;
        ThesisErrorCode lastError = null;
        String lastErrorMessage = null;
        boolean lastRawAvailable = false;
        while (attempt <= properties.getMaxRetries()) {
            attempt++;
            long started = System.currentTimeMillis();
            GenerateContentResponse response;
            try {
                response = caller.call(properties.getGeminiModelId(), userContent, config);
            } catch (ThesisTimeoutException e) {
                lastError = ThesisErrorCode.TIMEOUT;
                lastErrorMessage = e.getMessage();
                lastRawAvailable = false;
                continue;
            } catch (Exception e) {
                lastError = ThesisErrorCode.SCHEMA_VALIDATION_FAILED;
                lastErrorMessage = e.getMessage();
                lastRawAvailable = false;
                continue;
            }
            int latencyMs = (int) (System.currentTimeMillis() - started);

            String rawText = response.text();
            try {
                ThesisOutput output = objectMapper.readValue(rawText, ThesisOutput.class);
                return new ThesisGenerationResult.Success(
                        request.requestId(), properties.getGeminiModelId(), properties.getGeminiModelId(),
                        properties.getPromptVersion(), latencyMs, output);
            } catch (Exception parseError) {
                log.warn("Gemini response did not parse as ThesisOutput on attempt {}/{}: {}",
                        attempt, properties.getMaxRetries() + 1, parseError.getMessage());
                lastError = ThesisErrorCode.SCHEMA_VALIDATION_FAILED;
                lastErrorMessage = "Response did not conform to thesis-output.schema.json: " + parseError.getMessage();
                lastRawAvailable = true;
            }
        }

        return new ThesisGenerationResult.Failure(request.requestId(), lastError, lastErrorMessage, lastRawAvailable);
    }

    private String validate(ThesisInput input) {
        if (input == null) return "input is required";
        if (!StringUtils.hasText(input.symbol())) return "symbol is required";
        if (input.analysisDate() == null) return "analysisDate is required";
        if (input.marketPrice() == null || input.marketPrice().signum() <= 0) return "marketPrice must be > 0";
        if (input.dataQuality() == null) return "dataQuality is required";
        if (input.revenueTrend() == null || input.earningsTrend() == null || input.freeCashFlowTrend() == null) {
            return "revenueTrend/earningsTrend/freeCashFlowTrend are required";
        }
        if (input.deterministicWarnings() == null) return "deterministicWarnings is required (may be empty)";
        return null;
    }
}
