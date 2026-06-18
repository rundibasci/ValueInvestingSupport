package it.mazzoni.vis.api.dto;

import it.mazzoni.vis.domain.entity.Recommendation;
import java.math.BigDecimal;
import java.time.LocalDate;

public record QuickAnalysisResponse(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        String currency,
        String sector,
        FinancialSummary financialSummary,
        ValuationSummary valuation,
        BigDecimal marginOfSafety,
        Recommendation recommendation,
        String disclaimer,
        LocalDate dataAsOf,
        String source
) {
    public static final String DISCLAIMER =
            "This is a decision-support tool, not investment advice (MiFID II).";

    public record FinancialSummary(
            BigDecimal revenue,
            BigDecimal netIncome,
            BigDecimal fcf,
            BigDecimal eps
    ) {}

    public record ValuationSummary(
            DcfRange dcf,
            BigDecimal grahamNumber,
            BigDecimal composite
    ) {}

    public record DcfRange(
            BigDecimal fairValue,
            BigDecimal low,
            BigDecimal high
    ) {}
}
