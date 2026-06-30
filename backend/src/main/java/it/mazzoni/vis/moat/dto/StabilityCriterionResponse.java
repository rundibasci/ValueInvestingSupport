package it.mazzoni.vis.moat.dto;

import it.mazzoni.vis.domain.entity.StabilityResult;

import java.math.BigDecimal;

public record StabilityCriterionResponse(
        String criterionCode,
        String label,
        String status,
        BigDecimal actualValue,
        String message
) {
    public static StabilityCriterionResponse from(StabilityResult result) {
        return new StabilityCriterionResponse(
                result.getCriterionCode(),
                result.getLabel(),
                result.getStatus(),
                result.getActualValue(),
                result.getMessage()
        );
    }
}
