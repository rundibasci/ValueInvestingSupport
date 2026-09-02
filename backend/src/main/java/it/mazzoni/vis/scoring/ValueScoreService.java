package it.mazzoni.vis.scoring;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationBandResult;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.config.ScoringRiskProperties;
import it.mazzoni.vis.config.SectorMetricProperties;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.moat.ValuationHistoryService;
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
    private final ScoringRiskProperties scoringRiskProperties;
    private final SectorMetricProperties sectorMetricProperties;
    private final ValuationHistoryService valuationHistoryService;

    public ValueScoreService(
            SecurityRepository securityRepository,
            ValuationResultRepository valuationResultRepository,
            RatioSnapshotRepository ratioSnapshotRepository,
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            DividendRecordRepository dividendRecordRepository,
            ValueScoreRepository valueScoreRepository,
            ScoringRiskProperties scoringRiskProperties,
            SectorMetricProperties sectorMetricProperties,
            ValuationHistoryService valuationHistoryService) {
        this.securityRepository = securityRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.scoringRiskProperties = scoringRiskProperties;
        this.sectorMetricProperties = sectorMetricProperties;
        this.valuationHistoryService = valuationHistoryService;
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

        String weightProfileKey = determineWeightProfile(security, ratio, dividends);
        ScoringRiskProperties.WeightProfile profile = scoringRiskProperties.profile(weightProfileKey);

        // RM2 (specs/sector-aware-valuation-metrics.md §2): the metric-source branch is separate
        // from, and narrower than, the weight-profile branch above — REIT/real-estate only, never
        // utility (SectorClassifier.isReit vs. isReitOrUtility). Every other sector's formulas
        // below are byte-for-byte unchanged from before this phase.
        boolean isReit = SectorClassifier.isReit(security.getSector());

        BigDecimal mosScore, qualityScore, safetyScore, growthScore, dividendScore;
        if (isReit) {
            List<RatioSnapshot> annualRatios = ratioSnapshotRepository
                    .findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                    .stream().limit(2).toList();
            FundamentalSnapshot latestAnnual = annuals.isEmpty() ? null : annuals.get(0);
            mosScore      = scaleScore(computeMosScoreReit(security), new BigDecimal("30"), profile.mos());
            qualityScore  = scaleScore(computeQualityScoreReit(latestAnnual), new BigDecimal("25"), profile.quality());
            safetyScore   = scaleScore(computeSafetyScoreReit(ratio), new BigDecimal("20"), profile.safety());
            growthScore   = scaleScore(computeGrowthScoreReit(annualRatios), new BigDecimal("15"), profile.growth());
            dividendScore = scaleScore(computeDividendScoreReit(ratio), new BigDecimal("10"), profile.dividend());
        } else {
            mosScore      = scaleScore(computeMosScore(valuation), new BigDecimal("30"), profile.mos());
            qualityScore  = scaleScore(computeQualityScore(ratio), new BigDecimal("25"), profile.quality());
            safetyScore   = scaleScore(computeSafetyScore(ratio), new BigDecimal("20"), profile.safety());
            growthScore   = scaleScore(computeGrowthScore(annuals), new BigDecimal("15"), profile.growth());
            dividendScore = scaleScore(computeDividendScore(ratio, dividends), new BigDecimal("10"), profile.dividend());
        }
        BigDecimal rawTotalScore = mosScore.add(qualityScore).add(safetyScore)
                .add(growthScore).add(dividendScore);
        boolean gateApplied = valuation != null
                && valuation.getMarginOfSafety() != null
                && valuation.getMarginOfSafety().compareTo(BigDecimal.ZERO) < 0
                && rawTotalScore.compareTo(new BigDecimal("40")) > 0;
        BigDecimal totalScore = gateApplied ? new BigDecimal("40") : rawTotalScore;

        LocalDate scoreDate = LocalDate.now();
        valueScoreRepository.deleteBySecurityAndScoreDate(security, scoreDate);

        ValueScore entity = new ValueScore();
        entity.setSecurity(security);
        entity.setScoreDate(scoreDate);
        entity.setMosScore(mosScore);
        entity.setQualityScore(qualityScore);
        entity.setSafetyScore(safetyScore);
        entity.setGrowthScore(growthScore);
        entity.setDividendScore(dividendScore);
        entity.setTotalScore(totalScore);
        entity.setRawTotalScore(rawTotalScore);
        entity.setMosGateApplied(gateApplied);
        entity.setWeightProfile(weightProfileKey);
        return valueScoreRepository.save(entity);
    }

    private String determineWeightProfile(Security security, RatioSnapshot ratio, List<DividendRecord> dividends) {
        if (SectorClassifier.isReitOrUtility(security.getSector())) {
            return "reit-utility";
        }
        String sector = security.getSector() != null ? security.getSector().toLowerCase() : "";
        if (sector.contains("financial")) {
            return "financial";
        }
        if (sector.contains("cyclical") || sector.contains("basic material") || sector.contains("energy")) {
            return "cyclical";
        }
        boolean paysDividend = (ratio != null && ratio.getDividendYield() != null
                && ratio.getDividendYield().compareTo(BigDecimal.ZERO) > 0)
                || !dividends.isEmpty();
        return paysDividend ? "dividend-paying" : "non-dividend-growth";
    }

    private BigDecimal scaleScore(BigDecimal score, BigDecimal originalMax, BigDecimal targetMax) {
        if (score == null || targetMax == null || targetMax.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return score.multiply(targetMax).divide(originalMax, 2, RoundingMode.HALF_UP);
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

    // RM2 (specs/sector-aware-valuation-metrics.md §2, §4.3) REIT MoS sub-score (0–30): a
    // *relative-valuation* signal — P/FFO percentile position vs. this security's own history —
    // not an intrinsic-value margin of safety like computeMosScore above. Reuses
    // ValuationHistoryService's percentile-band machinery (Group MA) rather than DCF/Graham
    // composite fair value, since REIT DCF requires property-level cash-flow modeling this
    // platform does not attempt. Computed live (not read from a cached ValuationBandResult row)
    // because ValuationHistoryService.compute() is itself an on-demand recomputation elsewhere in
    // this codebase (MoatController, SecurityReviewService) — there is no ingestion-time job that
    // populates ValuationBandResult, so a REIT's very first score computation after seeding would
    // otherwise find no persisted "P_FFO" row at all.
    private BigDecimal computeMosScoreReit(Security security) {
        ValuationBandResult pFfoBand = valuationHistoryService.compute(security).stream()
                .filter(b -> "P_FFO".equals(b.getMetric()))
                .findFirst().orElse(null);
        if (pFfoBand == null || pFfoBand.getCurrentPercentile() == null) return BigDecimal.ZERO;
        BigDecimal percentile = pFfoBand.getCurrentPercentile();
        if (percentile.compareTo(new BigDecimal("25")) <= 0) return new BigDecimal("30");
        if (percentile.compareTo(new BigDecimal("50")) <= 0) return new BigDecimal("20");
        if (percentile.compareTo(new BigDecimal("75")) <= 0) return new BigDecimal("10");
        return BigDecimal.ZERO;
    }

    // RM2 REIT Quality sub-score (0–25): FFO margin (FFO ÷ Revenue) in place of ROIC/ROE — this
    // phase's own calibration of the source doc's illustrative "QUALITY_FFO_MARGIN" name, which
    // §4 of specs/sector-aware-valuation-metrics.md never defines a formula for (see
    // requirements.md → Decision 6). Computed directly from the latest ANNUAL FundamentalSnapshot
    // (netIncome + D&A) ÷ revenue — same period, avoids mixing a TTM per-share FFO figure with an
    // ANNUAL revenue figure. Thresholds are first-pass, config-driven (SectorMetricProperties),
    // not sourced from a published benchmark.
    private BigDecimal computeQualityScoreReit(FundamentalSnapshot latestAnnual) {
        if (latestAnnual == null || latestAnnual.getNetIncome() == null
                || latestAnnual.getDepreciationAndAmortization() == null
                || latestAnnual.getRevenue() == null || latestAnnual.getRevenue().compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ffo = latestAnnual.getNetIncome().add(latestAnnual.getDepreciationAndAmortization());
        BigDecimal ffoMargin = ffo.divide(latestAnnual.getRevenue(), 4, RoundingMode.HALF_UP);
        if (ffoMargin.compareTo(sectorMetricProperties.qualityFfoMarginHigh()) >= 0) return new BigDecimal("25");
        if (ffoMargin.compareTo(sectorMetricProperties.qualityFfoMarginMid()) >= 0) return new BigDecimal("18");
        if (ffoMargin.compareTo(sectorMetricProperties.qualityFfoMarginLow()) >= 0) return new BigDecimal("10");
        return BigDecimal.ZERO;
    }

    // RM2 REIT Safety sub-score (0–20): Net Debt/EBITDA thresholds + interest-coverage bonus, in
    // place of Debt/Equity + current-ratio — same three-tier-plus-bonus shape as
    // computeSafetyScore above, recalibrated for REIT-sector leverage norms
    // (SectorMetricProperties, first-pass calibration — see requirements.md → Decision 7).
    private BigDecimal computeSafetyScoreReit(RatioSnapshot ratio) {
        if (ratio == null || ratio.getNetDebtToEbitda() == null) return BigDecimal.ZERO;
        BigDecimal ndte = ratio.getNetDebtToEbitda();
        BigDecimal base;
        if (ndte.compareTo(sectorMetricProperties.safetyNetDebtEbitdaConservative()) <= 0) base = new BigDecimal("20");
        else if (ndte.compareTo(sectorMetricProperties.safetyNetDebtEbitdaModerate()) <= 0) base = new BigDecimal("14");
        else if (ndte.compareTo(sectorMetricProperties.safetyNetDebtEbitdaHigh()) <= 0) base = new BigDecimal("7");
        else base = BigDecimal.ZERO;

        if (base.compareTo(BigDecimal.ZERO) > 0
                && ratio.getInterestCoverageEbitda() != null
                && ratio.getInterestCoverageEbitda().compareTo(sectorMetricProperties.safetyInterestCoverageBonusThreshold()) >= 0) {
            base = base.add(new BigDecimal("2")).min(new BigDecimal("20"));
        }
        return base;
    }

    // RM2 REIT Growth sub-score (0–15): FFO-per-share YoY growth in place of revenue growth, same
    // thresholds as computeGrowthScore (no sector-specific growth-threshold guidance exists in the
    // source doc, so the existing bands are reused verbatim).
    private BigDecimal computeGrowthScoreReit(List<RatioSnapshot> annualRatios) {
        if (annualRatios.size() < 2) return BigDecimal.ZERO;
        BigDecimal latest = annualRatios.get(0).getFfoPerShare();
        BigDecimal prior  = annualRatios.get(1).getFfoPerShare();
        if (latest == null || prior == null || prior.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        BigDecimal growth = latest.subtract(prior).divide(prior, 4, RoundingMode.HALF_UP);
        if (growth.compareTo(new BigDecimal("0.10")) >= 0) return new BigDecimal("15");
        if (growth.compareTo(new BigDecimal("0.05")) >= 0) return new BigDecimal("10");
        if (growth.compareTo(BigDecimal.ZERO) >= 0)        return new BigDecimal("5");
        return BigDecimal.ZERO;
    }

    // RM2 REIT Dividend sub-score (0–10): AFFO payout ratio in place of yield+streak — the source
    // doc frames AFFO payout ratio as *the* sustainability check for this sector, not one input
    // among several (specs/sector-aware-valuation-metrics.md §4.6). First-pass thresholds,
    // SectorMetricProperties (see requirements.md → Decision 7).
    private BigDecimal computeDividendScoreReit(RatioSnapshot ratio) {
        if (ratio == null || ratio.getAffoPayoutRatio() == null) return BigDecimal.ZERO;
        BigDecimal payout = ratio.getAffoPayoutRatio();
        if (payout.compareTo(sectorMetricProperties.dividendAffoPayoutConservative()) <= 0) return new BigDecimal("10");
        if (payout.compareTo(sectorMetricProperties.dividendAffoPayoutModerate()) <= 0) return new BigDecimal("7");
        if (payout.compareTo(sectorMetricProperties.dividendAffoPayoutHigh()) <= 0) return new BigDecimal("4");
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
