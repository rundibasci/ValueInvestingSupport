package it.mazzoni.vis.portfolio.importing.dto;

import java.util.List;

public record PortfolioImportHistoryResponse(List<PortfolioImportHistoryItem> content, int page, int size,
        long totalElements, int totalPages) { }
