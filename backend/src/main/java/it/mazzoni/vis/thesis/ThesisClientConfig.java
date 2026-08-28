package it.mazzoni.vis.thesis;

import com.google.genai.Client;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Wires the real Vertex AI SDK client, only when {@code thesis.agent-enabled=true}
 * (THESIS_AGENT_ENABLED) — so no GCP credential is resolved, and no {@code Client} is
 * constructed at all, in every environment that leaves the feature off (the default).
 *
 * <p>Local/dev credentials: Application Default Credentials (ADC), this session's explicit
 * decision (mirroring TA3's precedent — no dedicated service-account key file for local
 * dev). No {@code .credentials(...)} override is passed to the builder, so the underlying
 * SDK resolves ADC itself. Production (K1+) binds a GCP service account to Cloud Run via
 * Secret Manager (specs/tech-stack.md -> GCP Distribution) — out of scope for this phase's
 * backend code (infrastructure work).
 */
@Configuration
@EnableConfigurationProperties(ThesisProperties.class)
public class ThesisClientConfig {

    @Bean
    public InvestmentThesisClient investmentThesisClient(ThesisProperties properties, GeminiCaller geminiCaller,
                                                           ThesisPromptLoader promptLoader) {
        return new VertexAiInvestmentThesisClient(properties, geminiCaller, promptLoader);
    }

    /** Fails fast at startup (not at first request) if the feature is enabled but
     * misconfigured — the adapted equivalent of TRAIN-12.2's MODEL_VERSION_UNAVAILABLE for a
     * single pinned GEMINI_MODEL_ID (no adapter-promotion registry exists here). */
    @Configuration
    static class StartupValidation {
        StartupValidation(ThesisProperties properties) {
            if (!properties.isAgentEnabled()) {
                return;
            }
            requireNonBlank(properties.getGeminiModelId(), "GEMINI_MODEL_ID");
            requireNonBlank(properties.getGoogleCloudProject(), "GOOGLE_CLOUD_PROJECT");
            requireNonBlank(properties.getVertexAiLocation(), "VERTEX_AI_LOCATION");
        }

        private static void requireNonBlank(String value, String envVar) {
            if (!StringUtils.hasText(value)) {
                throw new IllegalStateException(
                        "THESIS_AGENT_ENABLED=true requires " + envVar + " to be set");
            }
        }
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public GeminiCaller geminiCaller(ThesisProperties properties) {
        if (!properties.isAgentEnabled()) {
            return (model, userContent, config) -> {
                throw new IllegalStateException("THESIS_AGENT_ENABLED is false; no Vertex AI client was constructed");
            };
        }
        Client client = Client.builder()
                .vertexAI(true)
                .project(properties.getGoogleCloudProject())
                .location(properties.getVertexAiLocation())
                .build();
        return (model, userContent, config) -> client.models.generateContent(model, userContent, config);
    }
}
