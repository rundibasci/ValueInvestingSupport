package it.mazzoni.vis.domain;

import java.math.BigDecimal;

public record MarketPriceQuote(
        String symbol,
        BigDecimal price,
        String currency,
        BigDecimal change,
        BigDecimal changePercent,
        Long volume
) {}
