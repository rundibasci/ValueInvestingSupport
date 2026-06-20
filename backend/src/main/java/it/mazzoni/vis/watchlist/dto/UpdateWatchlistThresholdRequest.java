package it.mazzoni.vis.watchlist.dto;

import java.math.BigDecimal;

public record UpdateWatchlistThresholdRequest(
        BigDecimal mosAlertMin,
        BigDecimal mosAlertMax,
        BigDecimal fundamentalDegradeThreshold
) {}
