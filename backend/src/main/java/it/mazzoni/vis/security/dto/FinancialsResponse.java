package it.mazzoni.vis.security.dto;

import java.util.List;

public record FinancialsResponse(
        String symbol,
        List<AnnualFinancials> annuals,
        List<QuarterlyFinancials> quarters,
        TtmFinancials ttm
) {}
