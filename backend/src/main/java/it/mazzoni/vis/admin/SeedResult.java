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
        String error
) {
    static SeedResult success(String symbol, String companyName,
                              String sector, String exchange, String country,
                              String description, BigDecimal currentPrice,
                              BigDecimal compositeFairValue, BigDecimal marginOfSafety,
                              BigDecimal totalScore, Recommendation recommendation,
                              String source, LocalDate refreshedAt) {
        String fallbackReason = source != null && source.toLowerCase().contains("yahoo")
                ? "Yahoo Finance was used for at least one data category because primary FMP coverage was unavailable or restricted."
                : null;
        return new SeedResult(symbol, companyName, sector, exchange, country, description,
                currentPrice, compositeFairValue, marginOfSafety, totalScore, recommendation,
                source, "seeded", fallbackReason, refreshedAt, null);
    }

    static SeedResult failed(String symbol, String error) {
        String status = error != null && error.toLowerCase().contains("current fmp plan")
                ? "unavailable"
                : "failed";
        return new SeedResult(symbol, null, null, null, null, null, null, null, null, null,
                null, null, status, null, null, error);
    }
}
