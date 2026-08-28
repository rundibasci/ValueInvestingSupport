package it.mazzoni.vis.thesis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.mazzoni.vis.domain.entity.InvestmentThesisResult;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ThesisStatus;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.InvestmentThesisResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates {@link InvestmentThesisClient#generate} + persistence, reconciling TRAIN-12's
 * fallback-content rule with specs/tech-stack.md's {@code FAILED} status enum value: on
 * failure, the persisted row still carries a schema-conforming synthetic
 * {@code UNDER_REVIEW}/{@code humanReviewRequired=true} body, never a caller-visible raw or
 * malformed model output.
 *
 * <p>Dispatched via {@link CompletableFuture#runAsync}, mirroring
 * {@code it.mazzoni.vis.admin.JobAdminService}'s existing async-dispatch convention (no new
 * executor bean introduced).
 */
@Service
public class ThesisGenerationService {

    private static final Logger log = LoggerFactory.getLogger(ThesisGenerationService.class);

    private final InvestmentThesisClient client;
    private final ThesisInputSource inputBuilder;
    private final InvestmentThesisResultRepository repository;
    private final ThesisProperties properties;
    private final ObjectMapper objectMapper;

    public ThesisGenerationService(InvestmentThesisClient client, ThesisInputSource inputBuilder,
                                   InvestmentThesisResultRepository repository, ThesisProperties properties) {
        this.client = client;
        this.inputBuilder = inputBuilder;
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /** Persists the initial GENERATING row synchronously (so the accepted-response's
     * thesisRunId is immediately pollable), then dispatches generation asynchronously. */
    public UUID startGeneration(Security security, User requestedBy) {
        UUID requestId = UUID.randomUUID();

        InvestmentThesisResult initial = new InvestmentThesisResult();
        initial.setSecurity(security);
        initial.setRequestId(requestId);
        initial.setRequestedByUser(requestedBy);
        initial.setModelId(properties.getGeminiModelId());
        initial.setModelVersion(properties.getGeminiModelId());
        initial.setPromptVersion(properties.getPromptVersion());
        initial.setStatus(ThesisStatus.GENERATING);
        initial.setInputSnapshot("{}");
        repository.save(initial);

        CompletableFuture.runAsync(() -> runGeneration(requestId, security));
        return requestId;
    }

    // Package-private (not private) so tests can invoke it synchronously instead of racing
    // the CompletableFuture.runAsync dispatch in startGeneration.
    void runGeneration(UUID requestId, Security security) {
        ThesisInput input = inputBuilder.build(security);
        String inputJson = writeJson(input);

        ThesisGenerationRequest request = new ThesisGenerationRequest(requestId, properties.getGeminiModelId(), input);
        ThesisGenerationResult result = client.generate(request);

        InvestmentThesisResult row = repository.findByRequestId(requestId)
                .orElseThrow(() -> new IllegalStateException("Missing initial GENERATING row for requestId " + requestId));
        row.setInputSnapshot(inputJson);
        row.setGeneratedAt(LocalDateTime.now());

        switch (result) {
            case ThesisGenerationResult.Success success -> applySuccess(row, success);
            case ThesisGenerationResult.Failure failure -> applyFailure(row, failure);
        }

        repository.save(row);
    }

    private void applySuccess(InvestmentThesisResult row, ThesisGenerationResult.Success success) {
        ThesisOutput output = success.output();
        row.setModelId(success.modelId());
        row.setModelVersion(success.modelVersion());
        row.setPromptVersion(success.promptVersion());
        row.setLatencyMs(success.latencyMs());
        row.setOutputJson(writeJson(output));
        row.setClassification(output.classification() != null ? output.classification().name() : null);
        row.setConfidence(output.confidence());
        row.setHumanReviewRequired(output.humanReviewRequired());
        row.setDataWarningsPresent(output.dataWarnings() != null && !output.dataWarnings().isEmpty());
        row.setRawOutputAvailable(false);

        boolean needsReview = output.humanReviewRequired() || row.isDataWarningsPresent();
        row.setStatus(needsReview ? ThesisStatus.HUMAN_REVIEW_PENDING : ThesisStatus.READY);
    }

    private void applyFailure(InvestmentThesisResult row, ThesisGenerationResult.Failure failure) {
        log.warn("Thesis generation failed for requestId {}: {} ({})",
                row.getRequestId(), failure.errorCode(), failure.errorMessage());

        ThesisOutput fallback = ThesisOutput.deterministicFallback(
                failure.errorCode() + ": " + failure.errorMessage());
        row.setOutputJson(writeJson(fallback));
        row.setClassification(fallback.classification().name());
        row.setHumanReviewRequired(true);
        row.setDataWarningsPresent(true);
        row.setErrorCode(failure.errorCode() != null ? failure.errorCode().name() : null);
        row.setErrorMessage(failure.errorMessage());
        row.setRawOutputAvailable(failure.rawOutputAvailable());
        row.setStatus(ThesisStatus.FAILED);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize " + value.getClass().getSimpleName(), e);
        }
    }
}
