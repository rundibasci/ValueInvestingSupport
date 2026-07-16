package it.mazzoni.vis.portfolio.analysis;
import java.time.LocalDateTime;
import java.util.UUID;
public record PortfolioAnalysisStatusResponse(UUID analysisRunId, UUID portfolioId, UUID importId, String status, String phase, int total, int processed, int succeeded, int partial, int failed, String currentSymbol, String terminalReason, UUID analyticsSnapshotId, String analysisVersion, LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime updatedAt, LocalDateTime completedAt, int pollingIntervalMs) {}
