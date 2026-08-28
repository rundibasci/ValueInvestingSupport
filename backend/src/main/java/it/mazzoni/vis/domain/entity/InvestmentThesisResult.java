package it.mazzoni.vis.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Platform-wide reference data (like {@link ValuationResult}/{@link ValueScore}), not
 * user-owned — one row per AI-thesis generation, never updated in place (mission.md
 * Principle 6: immutable historical data). A regeneration always inserts a new row; the API
 * serves the latest one plus a computed {@code stale} flag. Adapts TRAIN-05/TRAIN-12's
 * runtime-contract design (vis-model-training/README.md §12.1-12.2) to a pinned
 * {@code GEMINI_MODEL_ID} instead of an adapter checksum — see
 * specs/2026-08-28-ta4-runtime-integration-contract/requirements.md.
 */
@Entity
@Table(name = "investment_thesis_result")
public class InvestmentThesisResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Security security;

    @Column(nullable = false, unique = true)
    private UUID requestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedByUser;

    @Column(nullable = false, length = 128)
    private String modelId;

    @Column(nullable = false, length = 64)
    private String modelVersion;

    @Column(nullable = false, length = 64)
    private String promptVersion;

    /** Exact thesis-input.schema.json-conforming JSON payload sent to Gemini, for audit/reproducibility. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String inputSnapshot;

    /** thesis-output.schema.json-conforming JSON, or the synthetic deterministic-fallback body; null only if never attempted. */
    @Column(columnDefinition = "TEXT")
    private String outputJson;

    @Column(length = 32)
    private String classification;

    @Column(precision = 3, scale = 2)
    private BigDecimal confidence;

    private Boolean humanReviewRequired;

    @Column(nullable = false)
    private boolean dataWarningsPresent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ThesisStatus status;

    @Column(length = 64)
    private String errorCode;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /** Whether raw (non-conforming) model output was retained for audit — never itself served as a substitute for a valid output (TRAIN-12.2). */
    @Column(nullable = false)
    private boolean rawOutputAvailable;

    private Integer latencyMs;

    private LocalDateTime generatedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID requestId) { this.requestId = requestId; }

    public User getRequestedByUser() { return requestedByUser; }
    public void setRequestedByUser(User requestedByUser) { this.requestedByUser = requestedByUser; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public String getPromptVersion() { return promptVersion; }
    public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }

    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }

    public String getOutputJson() { return outputJson; }
    public void setOutputJson(String outputJson) { this.outputJson = outputJson; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Boolean getHumanReviewRequired() { return humanReviewRequired; }
    public void setHumanReviewRequired(Boolean humanReviewRequired) { this.humanReviewRequired = humanReviewRequired; }

    public boolean isDataWarningsPresent() { return dataWarningsPresent; }
    public void setDataWarningsPresent(boolean dataWarningsPresent) { this.dataWarningsPresent = dataWarningsPresent; }

    public ThesisStatus getStatus() { return status; }
    public void setStatus(ThesisStatus status) { this.status = status; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public boolean isRawOutputAvailable() { return rawOutputAvailable; }
    public void setRawOutputAvailable(boolean rawOutputAvailable) { this.rawOutputAvailable = rawOutputAvailable; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(LocalDateTime generatedAt) { this.generatedAt = generatedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
