package it.mazzoni.vis.portfolio.dto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record RebalanceTarget(
        @NotBlank String symbol,
        @NotNull @DecimalMin("0.01") @DecimalMax("100") BigDecimal targetWeightPercent
) {}
