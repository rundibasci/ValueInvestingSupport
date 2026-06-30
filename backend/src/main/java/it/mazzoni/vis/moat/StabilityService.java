package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.StabilityResult;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.StabilityResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StabilityService {
    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final StabilityResultRepository stabilityResultRepository;

    public StabilityService(SecurityRepository securityRepository,
                            FundamentalSnapshotRepository fundamentalSnapshotRepository,
                            StabilityResultRepository stabilityResultRepository) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.stabilityResultRepository = stabilityResultRepository;
    }

    @Transactional
    public List<StabilityResult> assess(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
        return assess(security);
    }

    @Transactional
    public List<StabilityResult> assess(Security security) {
        List<FundamentalSnapshot> annuals = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream()
                .filter(f -> f.getFiscalYear() != null || f.getReportDate() != null)
                .limit(10)
                .toList();
        LocalDate resultDate = LocalDate.now();
        List<StabilityResult> results = new ArrayList<>();
        results.add(noNegativeEps(security, resultDate, annuals));
        results.add(noLargeEpsDecline(security, resultDate, annuals));
        results.add(positiveGrowth(security, resultDate, annuals, "positive_revenue_growth_10y", "Positive revenue growth over 10 years", true));
        results.add(positiveGrowth(security, resultDate, annuals, "positive_eps_growth_10y", "Positive EPS growth over 10 years", false));
        results.add(dividendContinuity(security, resultDate, annuals));

        stabilityResultRepository.deleteBySecurity(security);
        return stabilityResultRepository.saveAll(results);
    }

    private StabilityResult noNegativeEps(Security security, LocalDate date, List<FundamentalSnapshot> annuals) {
        if (annuals.size() < 10 || annuals.stream().anyMatch(a -> a.getEps() == null)) {
            return criterion(security, date, "no_negative_eps_10y", "No negative annual EPS in last 10 years", "INSUFFICIENT_DATA", null, "Ten annual EPS values are required.");
        }
        boolean pass = annuals.stream().allMatch(a -> a.getEps().compareTo(BigDecimal.ZERO) >= 0);
        BigDecimal min = annuals.stream().map(FundamentalSnapshot::getEps).min(Comparator.naturalOrder()).orElse(null);
        return criterion(security, date, "no_negative_eps_10y", "No negative annual EPS in last 10 years", pass ? "PASS" : "FAIL", min, null);
    }

    private StabilityResult noLargeEpsDecline(Security security, LocalDate date, List<FundamentalSnapshot> annuals) {
        List<FundamentalSnapshot> chronological = annuals.stream().sorted(Comparator.comparing(this::snapshotSortDate)).toList();
        if (chronological.size() < 10 || chronological.stream().anyMatch(a -> a.getEps() == null)) {
            return criterion(security, date, "no_large_eps_decline", "No year-over-year EPS decline greater than 33%", "INSUFFICIENT_DATA", null, "Ten annual EPS values are required.");
        }
        BigDecimal worstDecline = BigDecimal.ZERO;
        boolean pass = true;
        for (int i = 1; i < chronological.size(); i++) {
            BigDecimal previous = chronological.get(i - 1).getEps();
            BigDecimal current = chronological.get(i).getEps();
            if (previous.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal decline = previous.subtract(current).divide(previous, 6, java.math.RoundingMode.HALF_UP);
            if (decline.compareTo(worstDecline) > 0) worstDecline = decline;
            if (decline.compareTo(new BigDecimal("0.33")) > 0) pass = false;
        }
        return criterion(security, date, "no_large_eps_decline", "No year-over-year EPS decline greater than 33%", pass ? "PASS" : "FAIL", MoatMath.pct(worstDecline), null);
    }

    private StabilityResult positiveGrowth(Security security, LocalDate date, List<FundamentalSnapshot> annuals,
                                           String code, String label, boolean revenue) {
        if (annuals.size() < 10) {
            return criterion(security, date, code, label, "INSUFFICIENT_DATA", null, "Ten annual observations are required.");
        }
        FundamentalSnapshot latest = annuals.get(0);
        FundamentalSnapshot oldest = annuals.get(annuals.size() - 1);
        BigDecimal latestValue = revenue ? latest.getRevenue() : latest.getEps();
        BigDecimal oldestValue = revenue ? oldest.getRevenue() : oldest.getEps();
        BigDecimal change = MoatMath.percentChange(latestValue, oldestValue);
        if (change == null) {
            return criterion(security, date, code, label, "INSUFFICIENT_DATA", null, "Required values are missing or zero.");
        }
        return criterion(security, date, code, label, change.compareTo(BigDecimal.ZERO) > 0 ? "PASS" : "FAIL", change, null);
    }

    private StabilityResult dividendContinuity(Security security, LocalDate date, List<FundamentalSnapshot> annuals) {
        return criterion(security, date, "dividend_continuity_10y", "Dividend continuity for at least 10 years",
                "INSUFFICIENT_DATA", null, "Dividend-year continuity is not persisted in annual fundamentals yet.");
    }

    private StabilityResult criterion(Security security, LocalDate date, String code, String label, String status,
                                      BigDecimal actualValue, String message) {
        StabilityResult result = new StabilityResult();
        result.setSecurity(security);
        result.setResultDate(date);
        result.setCriterionCode(code);
        result.setLabel(label);
        result.setStatus(status);
        result.setActualValue(actualValue);
        result.setMessage(message);
        return result;
    }

    private LocalDate snapshotSortDate(FundamentalSnapshot snapshot) {
        if (snapshot.getReportDate() != null) return snapshot.getReportDate();
        if (snapshot.getFiscalYear() != null) return LocalDate.of(snapshot.getFiscalYear(), 12, 31);
        return LocalDate.MIN;
    }
}
