package it.mazzoni.vis.thesis;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code thesis.*} config block (application.yml) — env-var-overridable exactly like
 * every other integration in this project ({@code GOOGLE_CLOUD_PROJECT}, {@code
 * VERTEX_AI_LOCATION}, {@code GEMINI_MODEL_ID}, {@code THESIS_AGENT_ENABLED}, {@code
 * THESIS_GENERATION_DAILY_LIMIT}, {@code THESIS_GENERATION_MAX_RETRIES} — see
 * specs/tech-stack.md's environment-variable table).
 *
 * <p>Decoding values ({@code temperature}, {@code thinkingBudget}, {@code maxOutputTokens})
 * are fixed constants here, not configurable — they must match
 * vis-model-training/config/vertex-gemini-v1.json's pinned values exactly (verified by
 * ThesisConfigParityTest), since that file is the single source of truth for the request
 * shape TA3's capability benchmark actually validated.
 */
@ConfigurationProperties(prefix = "thesis")
public class ThesisProperties {

    /** Feature flag — defaults false until this phase's own acceptance suite (including the
     * v3 prompt corpus re-run) passes and a separate operational decision flips it. */
    private boolean agentEnabled = false;

    private String googleCloudProject;
    private String vertexAiLocation;
    private String geminiModelId;

    private int dailyLimit = 5;
    private int maxRetries = 1;
    private String promptVersion = "system-prompt-v3";

    public static final float TEMPERATURE = 0.0f;
    public static final int THINKING_BUDGET = 0;
    public static final int MAX_OUTPUT_TOKENS = 1024;

    public boolean isAgentEnabled() { return agentEnabled; }
    public void setAgentEnabled(boolean agentEnabled) { this.agentEnabled = agentEnabled; }

    public String getGoogleCloudProject() { return googleCloudProject; }
    public void setGoogleCloudProject(String googleCloudProject) { this.googleCloudProject = googleCloudProject; }

    public String getVertexAiLocation() { return vertexAiLocation; }
    public void setVertexAiLocation(String vertexAiLocation) { this.vertexAiLocation = vertexAiLocation; }

    public String getGeminiModelId() { return geminiModelId; }
    public void setGeminiModelId(String geminiModelId) { this.geminiModelId = geminiModelId; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }

    public int getMaxRetries() { return maxRetries; }
    public void setMaxRetries(int maxRetries) { this.maxRetries = maxRetries; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
}
