package it.mazzoni.vis.watchlist.dto;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;

public record AddWatchlistItemRequest(
        @NotBlank String symbol,
        BigDecimal mosAlertMin,
        BigDecimal mosAlertMax,
        BigDecimal fundamentalDegradeThreshold
) {}
