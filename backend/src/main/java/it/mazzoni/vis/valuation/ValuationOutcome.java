package it.mazzoni.vis.valuation;

import it.mazzoni.vis.domain.entity.ValuationResult;
import java.math.BigDecimal;
import java.util.Map;

public record ValuationOutcome(
        ValuationResult result,
        Map<String, BigDecimal> effectiveWeights,
        WaccResult waccResult,
        GrahamChecklistResult grahamChecklist
) {
    public ValuationOutcome(ValuationResult result, Map<String, BigDecimal> effectiveWeights) {
        this(result, effectiveWeights, null, null);
    }
}
