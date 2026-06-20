package it.mazzoni.vis.portfolio.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AddHoldingRequest(
        @NotBlank String symbol,
        @NotNull @Positive BigDecimal quantity,
        BigDecimal averageCostBasis,
        String currency
) {}
