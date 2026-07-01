package it.mazzoni.vis.professional.dto;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

public record ChecklistCriterionRequest(
        @NotBlank String label,
        @NotBlank String criterionType,
        String metricKey,
        String operator,
        BigDecimal threshold
) {
}
