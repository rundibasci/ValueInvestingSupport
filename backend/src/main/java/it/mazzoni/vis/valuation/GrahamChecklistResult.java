package it.mazzoni.vis.valuation;

import java.util.List;

public record GrahamChecklistResult(
        List<GrahamCriterionResult> criteria,
        int passed,
        int failed,
        int insufficientData
) {}
