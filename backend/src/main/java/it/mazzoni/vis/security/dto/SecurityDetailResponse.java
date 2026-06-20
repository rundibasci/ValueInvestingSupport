package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record SecurityDetailResponse(
        String symbol,
        String companyName,
        String sector,
        String exchange,
        String country,
        String currency,
        BigDecimal marketCap,
        String description,
        String website,
        BigDecimal currentPrice,
        LocalDate priceDate,
        BigDecimal revenue,
        BigDecimal netIncome,
        BigDecimal fcf,
        BigDecimal eps,
        BigDecimal bvps,
        BigDecimal pe,
        BigDecimal roic,
        BigDecimal dividendYield,
        LocalDate dataAsOf
) {
    public static SecurityDetailResponse from(Security s, FundamentalSnapshot f, RatioSnapshot r, PriceQuote p) {
        BigDecimal bvps = null;
        if (f.getTotalEquity() != null && f.getSharesOutstanding() != null && f.getSharesOutstanding() > 0) {
            bvps = f.getTotalEquity()
                    .divide(BigDecimal.valueOf(f.getSharesOutstanding()), 4, RoundingMode.HALF_UP);
        }
        return new SecurityDetailResponse(
                s.getSymbol(),
                s.getCompanyName(),
                s.getSector(),
                s.getExchange(),
                s.getCountry(),
                s.getCurrency(),
                s.getMarketCap(),
                s.getDescription(),
                s.getWebsite(),
                p != null ? p.getClose() : null,
                p != null ? p.getQuoteDate() : null,
                f.getRevenue(),
                f.getNetIncome(),
                f.getFreeCashFlow(),
                f.getEps(),
                bvps,
                r != null ? r.getPeRatio() : null,
                r != null ? r.getRoic() : null,
                r != null ? r.getDividendYield() : null,
                f.getReportDate()
        );
    }
}
