package it.mazzoni.vis.valuation;

import java.math.BigDecimal;

public record GrahamCriterionResult(
        String code,
        String label,
        GrahamCriterionStatus status,
        BigDecimal actualValue
) {}
