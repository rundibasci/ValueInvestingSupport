package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;

import java.math.BigDecimal;

public record TtmFinancials(
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal fcf,
        BigDecimal eps
) {
    public static TtmFinancials from(FundamentalSnapshot s) {
        return new TtmFinancials(s.getRevenue(), s.getNetIncome(), s.getFreeCashFlow(), s.getEps());
    }
}
