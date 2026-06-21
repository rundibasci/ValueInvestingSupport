package it.mazzoni.vis.portfolio.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record SimulationRequest(
        @NotNull @Positive BigDecimal budget,
        @DecimalMin(value = "0.01") @DecimalMax(value = "100") BigDecimal maxStockPercent,
        @DecimalMin(value = "0.01") @DecimalMax(value = "100") BigDecimal maxSectorPercent,
        @DecimalMin(value = "0.01") @DecimalMax(value = "100") BigDecimal maxCountryPercent,
        @DecimalMin("0.0") BigDecimal minimumMarginOfSafety,
        @DecimalMin("0.0") BigDecimal minimumDividendYield
) {}
