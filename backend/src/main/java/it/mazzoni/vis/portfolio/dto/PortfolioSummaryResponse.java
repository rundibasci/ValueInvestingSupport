package it.mazzoni.vis.portfolio.dto;

import it.mazzoni.vis.domain.entity.Portfolio;

import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioSummaryResponse(
        UUID id,
        String name,
        String description,
        int holdingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PortfolioSummaryResponse from(Portfolio p) {
        return new PortfolioSummaryResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getHoldings().size(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
