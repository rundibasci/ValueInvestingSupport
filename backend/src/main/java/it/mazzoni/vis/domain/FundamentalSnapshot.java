package it.mazzoni.vis.domain;

import java.math.BigDecimal;
import java.util.List;

public record FundamentalSnapshot(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String country,
        String currency,
        BigDecimal currentPrice,
        BigDecimal epsTtm,
        BigDecimal bookValuePerShare,
        Long sharesOutstanding,
        List<BigDecimal> revenueHistory,
        List<BigDecimal> netIncomeHistory,
        List<BigDecimal> fcfHistory,
        BigDecimal netDebt,
        BigDecimal totalDebt,
        BigDecimal cash
) {}
