package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;

import java.math.BigDecimal;

public record QuarterlyFinancials(
        String period,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal fcf,
        BigDecimal eps
) {
    public static QuarterlyFinancials from(FundamentalSnapshot s) {
        String period = (s.getFiscalQuarter() != null && s.getFiscalYear() != null)
                ? "Q" + s.getFiscalQuarter() + "-" + s.getFiscalYear()
                : (s.getReportDate() != null ? s.getReportDate().toString() : "Unknown");
        return new QuarterlyFinancials(period, s.getRevenue(), s.getNetIncome(),
                s.getFreeCashFlow(), s.getEps());
    }
}
