package it.mazzoni.vis.portfolio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PortfolioDetailResponse(
        UUID id,
        String name,
        String description,
        BigDecimal totalValue,
        BigDecimal weightedMoS,
        List<HoldingDetailItem> holdings,
        List<ConcentrationWarning> concentrationWarnings,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
