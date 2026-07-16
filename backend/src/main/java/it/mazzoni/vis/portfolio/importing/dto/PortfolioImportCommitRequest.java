package it.mazzoni.vis.portfolio.importing.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PortfolioImportCommitRequest(String newPortfolioName, boolean replaceConfirmed,
        Set<UUID> skippedRowIds, List<IsinMappingRequest> mappings) {
    public PortfolioImportCommitRequest {
        skippedRowIds = skippedRowIds == null ? Set.of() : Set.copyOf(skippedRowIds);
        mappings = mappings == null ? List.of() : List.copyOf(mappings);
    }
}
