package it.mazzoni.vis.portfolio.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RebalanceProposalResponse(
        UUID id,
        String status,
        List<RebalanceLineResponse> lines,
        BigDecimal estimatedBuyValue,
        BigDecimal estimatedSellValue,
        LocalDateTime createdAt,
        LocalDateTime appliedAt,
        String disclaimer
) {}
