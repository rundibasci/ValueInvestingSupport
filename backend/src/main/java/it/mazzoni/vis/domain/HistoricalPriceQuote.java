package it.mazzoni.vis.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record HistoricalPriceQuote(
        String symbol,
        LocalDate date,
        BigDecimal close,
        Long volume
) {}
