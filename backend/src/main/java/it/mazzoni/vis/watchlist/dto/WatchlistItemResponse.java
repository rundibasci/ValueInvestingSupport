package it.mazzoni.vis.watchlist.dto;

import it.mazzoni.vis.domain.entity.WatchlistItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record WatchlistItemResponse(
        UUID id,
        String symbol,
        BigDecimal mosAlertMin,
        BigDecimal mosAlertMax,
        BigDecimal fundamentalDegradeThreshold,
        LocalDateTime addedAt
) {
    public static WatchlistItemResponse from(WatchlistItem item) {
        return new WatchlistItemResponse(
                item.getId(),
                item.getSymbol(),
                item.getMosAlertMin(),
                item.getMosAlertMax(),
                item.getFundamentalDegradeThreshold(),
                item.getAddedAt()
        );
    }
}
