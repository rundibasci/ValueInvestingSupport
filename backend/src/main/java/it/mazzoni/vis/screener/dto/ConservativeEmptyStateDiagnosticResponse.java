package it.mazzoni.vis.screener.dto;

import java.util.List;

public record ConservativeEmptyStateDiagnosticResponse(
        List<ConservativeCriterionResponse> likelyEliminators,
        List<String> suggestedRelaxations,
        boolean currentCriteriaPreserved,
        String decisionSupportNote
) {
}
