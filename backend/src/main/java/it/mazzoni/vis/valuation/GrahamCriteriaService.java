package it.mazzoni.vis.valuation;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class GrahamCriteriaService {

    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final DividendRecordRepository dividendRecordRepository;

    public GrahamCriteriaService(
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            RatioSnapshotRepository ratioSnapshotRepository,
            DividendRecordRepository dividendRecordRepository) {
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.dividendRecordRepository = dividendRecordRepository;
    }

    public GrahamChecklistResult evaluate(Security security) {
        Optional<RatioSnapshot> latestRatio = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security);
        List<FundamentalSnapshot> annuals = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
        List<GrahamCriterionResult> criteria = new ArrayList<>();

        BigDecimal pe = latestRatio.map(RatioSnapshot::getPeRatio).orElse(null);
        BigDecimal pb = latestRatio.map(RatioSnapshot::getPbRatio).orElse(null);
        BigDecimal currentRatio = latestRatio.map(RatioSnapshot::getCurrentRatio).orElse(null);

        criteria.add(lessThan("pe_under_15", "P/E below 15", pe, new BigDecimal("15")));
        criteria.add(lessThan("pb_under_1_5", "P/B below 1.5", pb, new BigDecimal("1.5")));
        criteria.add(peTimesPb(pe, pb));
        criteria.add(greaterThan("current_ratio_above_2", "Current ratio above 2.0", currentRatio, new BigDecimal("2.0")));
        criteria.add(noNegativeEarnings(annuals));
        criteria.add(earningsStability(annuals));
        criteria.add(positiveTenYearEpsGrowth(annuals));
        criteria.add(dividendRecord(security));

        int passed = (int) criteria.stream().filter(c -> c.status() == GrahamCriterionStatus.PASS).count();
        int failed = (int) criteria.stream().filter(c -> c.status() == GrahamCriterionStatus.FAIL).count();
        int insufficient = (int) criteria.stream().filter(c -> c.status() == GrahamCriterionStatus.INSUFFICIENT_DATA).count();
        return new GrahamChecklistResult(criteria, passed, failed, insufficient);
    }

    private GrahamCriterionResult lessThan(String code, String label, BigDecimal actual, BigDecimal threshold) {
        if (actual == null) {
            return insufficient(code, label);
        }
        return new GrahamCriterionResult(code, label,
                actual.compareTo(threshold) < 0 ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL,
                actual);
    }

    private GrahamCriterionResult greaterThan(String code, String label, BigDecimal actual, BigDecimal threshold) {
        if (actual == null) {
            return insufficient(code, label);
        }
        return new GrahamCriterionResult(code, label,
                actual.compareTo(threshold) > 0 ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL,
                actual);
    }

    private GrahamCriterionResult peTimesPb(BigDecimal pe, BigDecimal pb) {
        String code = "pe_pb_under_22_5";
        if (pe == null || pb == null) {
            return insufficient(code, "P/E times P/B below 22.5");
        }
        BigDecimal product = pe.multiply(pb);
        return new GrahamCriterionResult(code, "P/E times P/B below 22.5",
                product.compareTo(new BigDecimal("22.5")) < 0 ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL,
                product);
    }

    private GrahamCriterionResult noNegativeEarnings(List<FundamentalSnapshot> annuals) {
        String code = "no_negative_earnings_5y";
        if (annuals.size() < 5 || annuals.stream().limit(5).anyMatch(s -> s.getEpsDiluted() == null)) {
            return insufficient(code, "No negative earnings in last 5 years");
        }
        boolean pass = annuals.stream().limit(5)
                .allMatch(s -> s.getEpsDiluted().compareTo(BigDecimal.ZERO) >= 0);
        return new GrahamCriterionResult(code, "No negative earnings in last 5 years",
                pass ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL, null);
    }

    private GrahamCriterionResult earningsStability(List<FundamentalSnapshot> annuals) {
        String code = "earnings_stability_10y";
        if (annuals.size() < 10 || annuals.stream().limit(10).anyMatch(s -> s.getEpsDiluted() == null)) {
            return insufficient(code, "10-year earnings stability");
        }
        List<BigDecimal> eps = annuals.stream().limit(10).map(FundamentalSnapshot::getEpsDiluted).toList();
        for (int i = 0; i < eps.size() - 1; i++) {
            BigDecimal current = eps.get(i);
            BigDecimal prior = eps.get(i + 1);
            if (prior.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal decline = prior.subtract(current).divide(prior, 6, java.math.RoundingMode.HALF_UP);
                if (decline.compareTo(new BigDecimal("0.33")) > 0) {
                    return new GrahamCriterionResult(code, "10-year earnings stability",
                            GrahamCriterionStatus.FAIL, decline);
                }
            }
        }
        return new GrahamCriterionResult(code, "10-year earnings stability", GrahamCriterionStatus.PASS, null);
    }

    private GrahamCriterionResult positiveTenYearEpsGrowth(List<FundamentalSnapshot> annuals) {
        String code = "positive_eps_growth_10y";
        if (annuals.size() < 10 || annuals.get(0).getEpsDiluted() == null || annuals.get(9).getEpsDiluted() == null) {
            return insufficient(code, "Positive EPS growth over 10 years");
        }
        BigDecimal latest = annuals.get(0).getEpsDiluted();
        BigDecimal oldest = annuals.get(9).getEpsDiluted();
        return new GrahamCriterionResult(code, "Positive EPS growth over 10 years",
                latest.compareTo(oldest) > 0 ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL,
                latest.subtract(oldest));
    }

    private GrahamCriterionResult dividendRecord(Security security) {
        String code = "dividend_record_10y";
        List<DividendRecord> dividends = dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security);
        LocalDate cutoff = LocalDate.now().minusYears(10);
        long years = dividends.stream()
                .filter(d -> !d.getExDividendDate().isBefore(cutoff))
                .map(d -> d.getExDividendDate().getYear())
                .distinct()
                .count();
        return new GrahamCriterionResult(code, "Dividend record at least 10 years",
                years >= 10 ? GrahamCriterionStatus.PASS : GrahamCriterionStatus.FAIL,
                BigDecimal.valueOf(years));
    }

    private GrahamCriterionResult insufficient(String code, String label) {
        return new GrahamCriterionResult(code, label, GrahamCriterionStatus.INSUFFICIENT_DATA, null);
    }
}
