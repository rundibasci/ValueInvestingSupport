package it.mazzoni.vis.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ValuationRequest(
        @NotNull @DecimalMin("0.001") BigDecimal wacc,
        @NotNull BigDecimal growthY1Y5,
        @NotNull BigDecimal growthY6Y10,
        @NotNull @DecimalMin("0.001") BigDecimal terminalRate,
        BigDecimal requiredReturn,
        BigDecimal dividendGrowthRate
) {}
