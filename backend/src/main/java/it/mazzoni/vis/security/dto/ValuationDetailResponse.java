package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ValuationDetailResponse(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        DcfScenarios dcf,
        BigDecimal grahamNumber,
        BigDecimal ddmValue,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal mosLow,
        BigDecimal mosHigh,
        String recommendation,
        AnalystEstimatesItem analystEstimates,
        LocalDate dataAsOf,
        String disclaimer
) {
    public static final String MIFID_DISCLAIMER =
            "This is a decision-support tool, not investment advice (MiFID II).";
}
