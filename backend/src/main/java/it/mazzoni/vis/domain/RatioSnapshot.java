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
        BigDecimal quickRatio,
        BigDecimal debtToEquity,
        BigDecimal interestCoverage,
        BigDecimal dividendYield,
        BigDecimal payoutRatio,
        BigDecimal beta,
        BigDecimal grossMargin,
        BigDecimal operatingMargin,
        BigDecimal netMargin
) {
    public RatioSnapshot(String symbol,
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
                         BigDecimal beta) {
        this(symbol, peRatio, forwardPeRatio, priceToBook, roe, roa, roic,
                currentRatio, null, debtToEquity, null, dividendYield, payoutRatio, beta,
                null, null, null);
    }

    public RatioSnapshot(String symbol,
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
                         BigDecimal beta,
                         BigDecimal grossMargin,
                         BigDecimal operatingMargin,
                         BigDecimal netMargin) {
        this(symbol, peRatio, forwardPeRatio, priceToBook, roe, roa, roic,
                currentRatio, null, debtToEquity, null, dividendYield, payoutRatio, beta,
                grossMargin, operatingMargin, netMargin);
    }
}
