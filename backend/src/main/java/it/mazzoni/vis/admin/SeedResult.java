package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.Recommendation;

import java.math.BigDecimal;

public record SeedResult(
        String symbol,
        String companyName,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        Recommendation recommendation,
        String error
) {
    static SeedResult success(String symbol, String companyName,
                              BigDecimal compositeFairValue, BigDecimal marginOfSafety,
                              Recommendation recommendation) {
        return new SeedResult(symbol, companyName, compositeFairValue, marginOfSafety, recommendation, null);
    }

    static SeedResult failed(String symbol, String error) {
        return new SeedResult(symbol, null, null, null, null, error);
    }
}
