package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.Recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SeedResult(
        String symbol,
        String companyName,
        String sector,
        String exchange,
        String country,
        String description,
        BigDecimal currentPrice,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        BigDecimal totalScore,
        Recommendation recommendation,
        String source,
        String status,
        String fallbackReason,
        LocalDate refreshedAt,
        String reasonCode,
        String reason,
        String error
) {
    private static final String FALLBACK_REASON =
            "Yahoo Finance was used for at least one data category because primary FMP coverage was unavailable or restricted.";
    static final String VALUATION_GUARDRAIL_BLOCKED = "valuation_guardrail_blocked";
    static final String VALUATION_GUARDRAIL_REASON =
            "Market data was saved, but no valuation model passed its eligibility guardrails.";

    static SeedResult success(String symbol, String companyName,
                              String sector, String exchange, String country,
                              String description, BigDecimal currentPrice,
                              BigDecimal compositeFairValue, BigDecimal marginOfSafety,
                              BigDecimal totalScore, Recommendation recommendation,
                              String source, LocalDate refreshedAt) {
        String fallbackReason = fallbackReason(source);
        return new SeedResult(symbol, companyName, sector, exchange, country, description,
                currentPrice, compositeFairValue, marginOfSafety, totalScore, recommendation,
                source, "seeded", fallbackReason, refreshedAt, null, null, null);
    }

    static SeedResult partial(String symbol, String companyName,
                              String sector, String exchange, String country,
                              String description, BigDecimal currentPrice,
                              String source, LocalDate refreshedAt) {
        return new SeedResult(symbol, companyName, sector, exchange, country, description,
                currentPrice, null, null, null, null, source, "seeded_partial",
                fallbackReason(source), refreshedAt, VALUATION_GUARDRAIL_BLOCKED,
                VALUATION_GUARDRAIL_REASON, null);
    }

    static SeedResult failed(String symbol, String error) {
        String status = error != null && error.toLowerCase().contains("current fmp plan")
                ? "unavailable"
                : "failed";
        return new SeedResult(symbol, null, null, null, null, null, null, null, null, null,
                null, null, status, null, null, null, null, error);
    }

    private static String fallbackReason(String source) {
        return source != null && source.toLowerCase().contains("yahoo") ? FALLBACK_REASON : null;
    }
}
