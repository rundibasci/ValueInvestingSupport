package it.mazzoni.vis.portfolio.analysis;
import java.util.UUID;
public record PortfolioAnalysisAcceptedResponse(UUID analysisRunId, String status, int total, String statusUrl, String outcomesUrl, int pollingIntervalMs, boolean joined) {}
