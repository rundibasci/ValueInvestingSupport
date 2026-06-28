package it.mazzoni.vis.watchlist.dto;

import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateWatchlistThresholdRequest(
        BigDecimal mosAlertMin,
        BigDecimal mosAlertMax,
        BigDecimal fundamentalDegradeThreshold,
        String monitoringReason,
        @Size(max = 500) String rationaleNote
) {}
