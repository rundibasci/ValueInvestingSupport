package it.mazzoni.vis.portfolio.importing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PortfolioImportCommitResponse(UUID importId, UUID portfolioId, String status, String mode,
        int committedHoldingRows, int committedCashRows, int skippedRows, BigDecimal baseValueTotal,
        Map<String, BigDecimal> nativeValueTotals, LocalDateTime committedAt,
        List<PortfolioImportRowResponse> rows) { }
