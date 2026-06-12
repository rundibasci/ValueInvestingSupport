package it.mazzoni.vis.demo.dto;

import java.math.BigDecimal;

public record DemoAnalysisResponse(
        String symbol,
        String companyName,
        BigDecimal currentPrice,
        String currency,
        String sector,
        FinancialSummary financialSummary,
        Valuation valuation,
        BigDecimal marginOfSafety,
        Recommendation recommendation,
        String disclaimer
) {

    public record FinancialSummary(
            BigDecimal revenue,
            BigDecimal netIncome,
            BigDecimal fcf,
            BigDecimal eps
    ) {}

    public record DcfValuation(
            BigDecimal fairValue,
            BigDecimal low,
            BigDecimal high
    ) {}

    public record Valuation(
            DcfValuation dcf,
            BigDecimal grahamNumber,
            BigDecimal composite
    ) {}

    public static final String DISCLAIMER =
            "This is a decision-support tool, not investment advice (MiFID II).";
}
