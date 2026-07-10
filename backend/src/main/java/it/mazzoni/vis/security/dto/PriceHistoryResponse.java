package it.mazzoni.vis.security.dto;

import java.time.LocalDate;
import java.util.List;

public record PriceHistoryResponse(
        String symbol,
        String range,
        LocalDate from,
        LocalDate to,
        String source,
        List<PriceHistoryItem> prices
) {}
