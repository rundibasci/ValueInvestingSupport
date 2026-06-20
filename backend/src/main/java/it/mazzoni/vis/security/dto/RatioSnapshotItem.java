package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.domain.entity.RatioSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RatioSnapshotItem(
        LocalDate date,
        BigDecimal pe,
        BigDecimal roic,
        BigDecimal roe,
        BigDecimal debtToEquity,
        BigDecimal grossMargin,
        BigDecimal dividendYield
) {
    public static RatioSnapshotItem from(RatioSnapshot r) {
        return new RatioSnapshotItem(
                r.getReportDate(),
                r.getPeRatio(),
                r.getRoic(),
                r.getRoe(),
                r.getDebtToEquity(),
                r.getGrossMargin(),
                r.getDividendYield()
        );
    }
}
