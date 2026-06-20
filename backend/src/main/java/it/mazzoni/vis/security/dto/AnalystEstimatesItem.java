package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;

public record AnalystEstimatesItem(
        BigDecimal priceTargetMean,
        BigDecimal priceTargetLow,
        BigDecimal priceTargetHigh,
        int analystCount,
        String consensus
) {}
