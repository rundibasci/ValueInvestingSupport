package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record HoldingDetailItem(
        UUID id,
        String symbol,
        String sector,
        BigDecimal quantity,
        BigDecimal averageCostBasis,
        String currency,
        BigDecimal currentPrice,
        BigDecimal currentValue,
        BigDecimal weightPercent,
        BigDecimal compositeFairValue,
        BigDecimal marginOfSafety,
        String recommendation,
        String valueStatus,
        LocalDateTime addedAt
) {}
