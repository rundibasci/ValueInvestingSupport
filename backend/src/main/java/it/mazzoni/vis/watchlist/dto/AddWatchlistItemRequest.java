package it.mazzoni.vis.watchlist.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AddWatchlistItemRequest(
        @NotBlank String symbol,
        BigDecimal mosAlertMin,
        BigDecimal mosAlertMax,
        BigDecimal fundamentalDegradeThreshold,
        String monitoringReason,
        @Size(max = 500) String rationaleNote
) {}
