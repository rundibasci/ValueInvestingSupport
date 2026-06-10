package it.mazzoni.vis.domain;

import java.math.BigDecimal;

public record RatioSnapshot(
        String symbol,
        BigDecimal peRatio,
        BigDecimal forwardPeRatio,
        BigDecimal priceToBook,
        BigDecimal roe,
        BigDecimal roa,
        BigDecimal roic,
        BigDecimal currentRatio,
        BigDecimal debtToEquity,
        BigDecimal dividendYield,
        BigDecimal payoutRatio,
        BigDecimal beta
) {}
