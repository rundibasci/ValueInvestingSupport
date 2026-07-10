package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.HistoricalPriceQuote;
import it.mazzoni.vis.domain.entity.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceHistoryItem(
        LocalDate date,
        BigDecimal close,
        Long volume
) {
    public static PriceHistoryItem from(HistoricalPriceQuote quote) {
        return new PriceHistoryItem(quote.date(), quote.close(), quote.volume());
    }

    public static PriceHistoryItem from(PriceQuote quote) {
        return new PriceHistoryItem(quote.getQuoteDate(), quote.getClose(), null);
    }
}
