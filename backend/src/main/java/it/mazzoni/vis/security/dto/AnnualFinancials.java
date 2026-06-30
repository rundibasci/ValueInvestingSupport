package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AnnualFinancials(
        Integer fiscalYear,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal fcf,
        BigDecimal eps,
        BigDecimal bvps,
        Long sharesOutstanding
) {
    public static AnnualFinancials from(FundamentalSnapshot s) {
        BigDecimal bvps = null;
        if (s.getTotalEquity() != null && s.getSharesOutstanding() != null && s.getSharesOutstanding() > 0) {
            bvps = s.getTotalEquity()
                    .divide(BigDecimal.valueOf(s.getSharesOutstanding()), 4, RoundingMode.HALF_UP);
        }
        return new AnnualFinancials(s.getFiscalYear(), s.getRevenue(), s.getNetIncome(),
                s.getFreeCashFlow(), s.getEps(), bvps, s.getSharesOutstanding());
    }
}
