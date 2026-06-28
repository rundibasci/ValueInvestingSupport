package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;

public record ConcentrationWarning(
        String type,
        String key,
        BigDecimal weightPercent,
        BigDecimal thresholdPercent,
        String message
) {}
