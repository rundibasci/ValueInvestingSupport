package it.mazzoni.vis.security.dto;

import java.math.BigDecimal;
import java.util.List;

public record DividendsResponse(
        String symbol,
        List<DividendItem> history,
        int streak,
        BigDecimal cagr3y,
        BigDecimal cagr5y,
        BigDecimal cagr10y
) {}
