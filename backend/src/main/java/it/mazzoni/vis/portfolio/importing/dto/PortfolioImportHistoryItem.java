package it.mazzoni.vis.portfolio.importing.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioImportHistoryItem(UUID importId, UUID portfolioId, String portfolioName, String filename,
        String checksum, String mode, String baseCurrency, String status, int sourceRowCount, int readyRowCount,
        int warningCount, int errorCount, LocalDateTime createdAt, LocalDateTime expiresAt,
        LocalDateTime committedAt) { }
