package it.mazzoni.vis.security.dto;

import java.util.List;

public record InsidersResponse(
        String symbol,
        List<InsiderTradeItem> trades
) {}
