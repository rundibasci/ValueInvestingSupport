package it.mazzoni.vis.scoring;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ValueScoreService {

    private final SecurityRepository securityRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final DividendRecordRepository dividendRecordRepository;
    private final ValueScoreRepository valueScoreRepository;

    public ValueScoreService(
            SecurityRepository securityRepository,
            ValuationResultRepository valuationResultRepository,
            RatioSnapshotRepository ratioSnapshotRepository,
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            DividendRecordRepository dividendRecordRepository,
            ValueScoreRepository valueScoreRepository) {
        this.securityRepository = securityRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.valueScoreRepository = valueScoreRepository;
    }

    public ValueScore compute(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        ValuationResult valuation = valuationResultRepository
                .findTopBySecurityOrderByValuationDateDesc(security).orElse(null);

        RatioSnapshot ratio = ratioSnapshotRepository
                .findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM)
                .stream().findFirst().orElse(null);

        List<FundamentalSnapshot> annuals = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream().limit(2).toList();

        List<DividendRecord> dividends = dividendRecordRepository
                .findBySecurityOrderByExDividendDateDesc(security);

        BigDecimal mosScore      = computeMosScore(valuation);
        BigDecimal qualityScore  = computeQualityScore(ratio);
        BigDecimal safetyScore   = computeSafetyScore(ratio);
        BigDecimal growthScore   = computeGrowthScore(annuals);
        BigDecimal dividendScore = computeDividendScore(ratio, dividends);
        BigDecimal totalScore    = mosScore.add(qualityScore).add(safetyScore)
                .add(growthScore).add(dividendScore);

        ValueScore entity = new ValueScore();
        entity.setSecurity(security);
        entity.setScoreDate(LocalDate.now());
        entity.setMosScore(mosScore);
        entity.setQualityScore(qualityScore);
        entity.setSafetyScore(safetyScore);
        entity.setGrowthScore(growthScore);
        entity.setDividendScore(dividendScore);
        entity.setTotalScore(totalScore);
        return valueScoreRepository.save(entity);
    }

    // MoS sub-score (0–30): based on marginOfSafety percentage from latest ValuationResult
    private BigDecimal computeMosScore(ValuationResult valuation) {
        if (valuation == null || valuation.getMarginOfSafety() == null) return BigDecimal.ZERO;
        BigDecimal mos = valuation.getMarginOfSafety();
        if (mos.compareTo(new BigDecimal("30")) >= 0) return new BigDecimal("30");
        if (mos.compareTo(new BigDecimal("15")) >= 0) return new BigDecimal("20");
        if (mos.compareTo(new BigDecimal("5"))  >= 0) return new BigDecimal("10");
        return BigDecimal.ZERO;
    }

    // Quality sub-score (0–25): ROIC primary, ROE fallback; ratios stored as decimals (0.15 = 15%)
    private BigDecimal computeQualityScore(RatioSnapshot ratio) {
        if (ratio == null) return BigDecimal.ZERO;
        BigDecimal metric = ratio.getRoic() != null ? ratio.getRoic() : ratio.getRoe();
        if (metric == null) return BigDecimal.ZERO;
        if (metric.compareTo(new BigDecimal("0.15")) >= 0) return new BigDecimal("25");
        if (metric.compareTo(new BigDecimal("0.10")) >= 0) return new BigDecimal("18");
        if (metric.compareTo(new BigDecimal("0.05")) >= 0) return new BigDecimal("10");
        return BigDecimal.ZERO;
    }

    // Safety sub-score (0–20): D/E thresholds + +2 bonus if currentRatio ≥ 2.0, capped at 20
    private BigDecimal computeSafetyScore(RatioSnapshot ratio) {
        if (ratio == null || ratio.getDebtToEquity() == null) return BigDecimal.ZERO;
        BigDecimal dte = ratio.getDebtToEquity();
        BigDecimal base;
        if (dte.compareTo(new BigDecimal("0.5")) <= 0)  base = new BigDecimal("20");
        else if (dte.compareTo(BigDecimal.ONE) <= 0)    base = new BigDecimal("14");
        else if (dte.compareTo(new BigDecimal("2.0")) <= 0) base = new BigDecimal("7");
        else base = BigDecimal.ZERO;

        if (base.compareTo(BigDecimal.ZERO) > 0
                && ratio.getCurrentRatio() != null
                && ratio.getCurrentRatio().compareTo(new BigDecimal("2.0")) >= 0) {
            base = base.add(new BigDecimal("2")).min(new BigDecimal("20"));
        }
        return base;
    }

    // Growth sub-score (0–15): year-over-year revenue growth from latest two annual snapshots
    private BigDecimal computeGrowthScore(List<FundamentalSnapshot> annuals) {
        if (annuals.size() < 2) return BigDecimal.ZERO;
        BigDecimal latest = annuals.get(0).getRevenue();
        BigDecimal prior  = annuals.get(1).getRevenue();
        if (latest == null || prior == null || prior.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal growth = latest.subtract(prior).divide(prior, 4, RoundingMode.HALF_UP);
        if (growth.compareTo(new BigDecimal("0.10")) >= 0) return new BigDecimal("15");
        if (growth.compareTo(new BigDecimal("0.05")) >= 0) return new BigDecimal("10");
        if (growth.compareTo(BigDecimal.ZERO) >= 0)        return new BigDecimal("5");
        return BigDecimal.ZERO;
    }

    // Dividend sub-score (0–10): yield ≥ 2% AND ≥ 5 consecutive years → 10; either alone → 5
    private BigDecimal computeDividendScore(RatioSnapshot ratio, List<DividendRecord> dividends) {
        BigDecimal yield = ratio != null ? ratio.getDividendYield() : null;
        int streak = countConsecutiveDividendYears(dividends);
        boolean highYield  = yield != null && yield.compareTo(new BigDecimal("0.02")) >= 0;
        boolean goodStreak = streak >= 5;
        if (highYield && goodStreak) return new BigDecimal("10");
        if (highYield || goodStreak) return new BigDecimal("5");
        return BigDecimal.ZERO;
    }

    private int countConsecutiveDividendYears(List<DividendRecord> records) {
        int currentYear = LocalDate.now().getYear();
        int consecutive = 0;
        for (int y = currentYear; y >= currentYear - 10; y--) {
            final int year = y;
            boolean hasDividend = records.stream()
                    .anyMatch(r -> r.getExDividendDate().getYear() == year);
            if (hasDividend) consecutive++;
            else break;
        }
        return consecutive;
    }
}
