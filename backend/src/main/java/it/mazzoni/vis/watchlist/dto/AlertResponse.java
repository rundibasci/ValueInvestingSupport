package it.mazzoni.vis.watchlist.dto;

import it.mazzoni.vis.domain.entity.Alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        String alertType,
        String symbol,
        BigDecimal threshold,
        LocalDateTime triggeredAt,
        String status,
        String priority,
        String deliveryStatus
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getAlertType().name(),
                alert.getSymbol(),
                alert.getThreshold(),
                alert.getTriggeredAt(),
                alert.getStatus().name(),
                alert.getPriority().name(),
                alert.getDeliveryStatus().name()
        );
    }
}
