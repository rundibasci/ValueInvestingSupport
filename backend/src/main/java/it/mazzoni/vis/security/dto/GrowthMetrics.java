package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;

public record GrowthMetrics(
        BigDecimal cagr3y,
        BigDecimal cagr5y,
        BigDecimal cagr10y
) {}
