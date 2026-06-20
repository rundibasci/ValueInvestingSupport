package it.mazzoni.vis.security.dto;

import java.util.List;

public record RatiosHistoryResponse(
        String symbol,
        List<RatioSnapshotItem> ratios
) {}
