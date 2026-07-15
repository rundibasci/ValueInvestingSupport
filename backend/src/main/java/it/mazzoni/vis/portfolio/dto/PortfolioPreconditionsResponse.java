package it.mazzoni.vis.portfolio.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PortfolioPreconditionsResponse(
        UUID portfolioId,
        boolean simulationAvailable,
        boolean rebalanceAvailable,
        int watchlistCount,
        int eligibleCandidateCount,
        int holdingCount,
        int unpricedHoldingCount,
        Map<String, Long> exclusionCounts,
        List<PortfolioPreconditionDiagnostic> diagnostics
) {}
