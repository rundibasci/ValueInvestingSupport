package it.mazzoni.vis.screener.dto;

import java.util.List;

public record ScreenerResponse(
        List<ScreenerResultItem> results,
        int page,
        int pageSize,
        long totalElements,
        int totalPages
) {}
