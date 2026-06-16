package it.mazzoni.vis.api.dto;

import it.mazzoni.vis.domain.entity.Recommendation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record ValuationResponse(
        String symbol,
        LocalDate valuationDate,
        BigDecimal dcfFairValue,
        BigDecimal dcfFairValueLow,
        BigDecimal dcfFairValueHigh,
        BigDecimal grahamNumber,
        BigDecimal ddmFairValue,
        BigDecimal compositeFairValue,
        BigDecimal currentPrice,
        BigDecimal marginOfSafety,
        Recommendation recommendation,
        String disclaimer,
        Map<String, BigDecimal> weights
) {
    public static final String DISCLAIMER =
            "This is a decision-support tool, not investment advice (MiFID II).";
}
