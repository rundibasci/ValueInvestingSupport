package it.mazzoni.vis.portfolio.importing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PortfolioImportPreviewResponse(UUID importId, UUID portfolioId, String filename, String checksum,
        String detectedSchema, String mode, String baseCurrency, String status, int sourceRowCount,
        int readyRowCount, int warningCount, int errorCount, BigDecimal baseValueTotal,
        Map<String, BigDecimal> nativeValueTotals, LocalDateTime createdAt, LocalDateTime expiresAt,
        List<PortfolioImportRowResponse> rows) { }
