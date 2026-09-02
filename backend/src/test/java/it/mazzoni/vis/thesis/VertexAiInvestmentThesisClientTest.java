package it.mazzoni.vis.thesis;

import com.google.genai.types.GenerateContentResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Every test injects a fake {@link GeminiCaller} — no network call, no real credentials,
 * matching TA3's Python VertexBackend test-isolation discipline. */
class VertexAiInvestmentThesisClientTest {

    private static final ThesisInput VALID_INPUT = new ThesisInput(
            "AAPL", "Apple Inc.", LocalDate.of(2026, 8, 28),
            new BigDecimal("150.00"), new BigDecimal("180.00"), new BigDecimal("16.67"),
            new BigDecimal("75"), new BigDecimal("0.5"), new BigDecimal("15"),
            new BigDecimal("0.8"), null, null, null, null, null,
            Trend.GROWING, Trend.GROWING, Trend.STABLE,
            DataQuality.COMPLETE, List.of());

    private static final String VALID_OUTPUT_JSON = """
            {"classification":"POTENTIALLY_UNDERVALUED","confidence":0.7,"summary":"s",
             "bullCase":[],"bearCase":[],"keyRisks":[],"keyAssumptions":[],
             "invalidationConditions":[],"dataWarnings":[],"humanReviewRequired":false}
            """;

    private ThesisProperties properties() {
        ThesisProperties properties = new ThesisProperties();
        properties.setAgentEnabled(true);
        properties.setGeminiModelId("gemini-2.5-flash");
        properties.setGoogleCloudProject("test-project");
        properties.setVertexAiLocation("europe-west1");
        properties.setMaxRetries(1);
        return properties;
    }

    private ThesisPromptLoader fakePromptLoader() {
        ThesisPromptLoader loader = mock(ThesisPromptLoader.class);
        when(loader.promptText()).thenReturn("You are the Investment Thesis Agent.");
        return loader;
    }

    private GenerateContentResponse responseWithText(String text) {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.text()).thenReturn(text);
        return response;
    }

    @Test
    void generate_returnsSuccess_onValidResponse() {
        GeminiCaller caller = (model, content, config) -> responseWithText(VALID_OUTPUT_JSON);
        VertexAiInvestmentThesisClient client = new VertexAiInvestmentThesisClient(properties(), caller, fakePromptLoader());

        ThesisGenerationResult result = client.generate(
                new ThesisGenerationRequest(UUID.randomUUID(), "gemini-2.5-flash", VALID_INPUT));

        assertThat(result).isInstanceOf(ThesisGenerationResult.Success.class);
        ThesisGenerationResult.Success success = (ThesisGenerationResult.Success) result;
        assertThat(success.output().classification()).isEqualTo(ThesisClassification.POTENTIALLY_UNDERVALUED);
    }

    @Test
    void generate_retriesOnceThenSucceeds_onFirstMalformedResponse() {
        AtomicInteger calls = new AtomicInteger();
        GeminiCaller caller = (model, content, config) -> {
            if (calls.getAndIncrement() == 0) {
                return responseWithText("not valid json");
            }
            return responseWithText(VALID_OUTPUT_JSON);
        };
        VertexAiInvestmentThesisClient client = new VertexAiInvestmentThesisClient(properties(), caller, fakePromptLoader());

        ThesisGenerationResult result = client.generate(
                new ThesisGenerationRequest(UUID.randomUUID(), "gemini-2.5-flash", VALID_INPUT));

        assertThat(result).isInstanceOf(ThesisGenerationResult.Success.class);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void generate_exhaustsRetries_thenReturnsSchemaValidationFailedFailure() {
        GeminiCaller caller = (model, content, config) -> responseWithText("still not json");
        VertexAiInvestmentThesisClient client = new VertexAiInvestmentThesisClient(properties(), caller, fakePromptLoader());

        ThesisGenerationResult result = client.generate(
                new ThesisGenerationRequest(UUID.randomUUID(), "gemini-2.5-flash", VALID_INPUT));

        assertThat(result).isInstanceOf(ThesisGenerationResult.Failure.class);
        ThesisGenerationResult.Failure failure = (ThesisGenerationResult.Failure) result;
        assertThat(failure.errorCode()).isEqualTo(ThesisErrorCode.SCHEMA_VALIDATION_FAILED);
        assertThat(failure.rawOutputAvailable()).isTrue();
    }

    @Test
    void generate_mapsTimeoutException_toTimeoutFailure() {
        GeminiCaller caller = (model, content, config) -> {
            throw new ThesisTimeoutException("deadline exceeded", null);
        };
        VertexAiInvestmentThesisClient client = new VertexAiInvestmentThesisClient(properties(), caller, fakePromptLoader());

        ThesisGenerationResult result = client.generate(
                new ThesisGenerationRequest(UUID.randomUUID(), "gemini-2.5-flash", VALID_INPUT));

        assertThat(result).isInstanceOf(ThesisGenerationResult.Failure.class);
        assertThat(((ThesisGenerationResult.Failure) result).errorCode()).isEqualTo(ThesisErrorCode.TIMEOUT);
    }

    @Test
    void generate_shortCircuitsWithNoCall_onMissingRequiredField() {
        List<Object> calls = new ArrayList<>();
        GeminiCaller caller = (model, content, config) -> {
            calls.add(content);
            return responseWithText(VALID_OUTPUT_JSON);
        };
        VertexAiInvestmentThesisClient client = new VertexAiInvestmentThesisClient(properties(), caller, fakePromptLoader());

        ThesisInput missingSymbol = new ThesisInput(
                " ", "Apple Inc.", LocalDate.now(), new BigDecimal("1"), null, null, null,
                null, null, null, null, null, null, null, null,
                Trend.STABLE, Trend.STABLE, Trend.STABLE, DataQuality.COMPLETE, List.of());

        ThesisGenerationResult result = client.generate(
                new ThesisGenerationRequest(UUID.randomUUID(), "gemini-2.5-flash", missingSymbol));

        assertThat(result).isInstanceOf(ThesisGenerationResult.Failure.class);
        assertThat(((ThesisGenerationResult.Failure) result).errorCode()).isEqualTo(ThesisErrorCode.INPUT_SCHEMA_INVALID);
        assertThat(calls).isEmpty();
    }
}
