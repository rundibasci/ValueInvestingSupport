package it.mazzoni.vis.scoring;

import it.mazzoni.vis.config.ScoringRiskProperties;
import it.mazzoni.vis.config.SectorMetricProperties;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationBandPosition;
import it.mazzoni.vis.domain.entity.ValuationBandResult;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.moat.ValuationHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValueScoreServiceTest {

    @Mock SecurityRepository securityRepository;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock DividendRecordRepository dividendRecordRepository;
    @Mock ValueScoreRepository valueScoreRepository;
    @Mock ScoringRiskProperties scoringRiskProperties;
    @Mock ValuationHistoryService valuationHistoryService;

    // RM2: a real instance (all-default values), not a mock — SectorMetricProperties has no
    // behavior beyond accessors, matching the source doc's "config-driven, not hardcoded" intent.
    SectorMetricProperties sectorMetricProperties =
            new SectorMetricProperties(null, null, null, null, null, null, null, null, null, null);

    ValueScoreService service;
    Security security;

    @BeforeEach
    void setUp() {
        service = new ValueScoreService(securityRepository, valuationResultRepository,
                ratioSnapshotRepository, fundamentalSnapshotRepository,
                dividendRecordRepository, valueScoreRepository, scoringRiskProperties,
                sectorMetricProperties, valuationHistoryService);
        security = new Security();
        security.setSymbol("AAPL");
        security.setSector("Technology");
        Mockito.lenient().when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        Mockito.lenient().when(valueScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(scoringRiskProperties.profile(Mockito.anyString())).thenAnswer(inv -> switch ((String) inv.getArgument(0)) {
            case "non-dividend-growth" -> new ScoringRiskProperties.WeightProfile(new BigDecimal("30"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("20"), BigDecimal.ZERO);
            case "reit-utility" -> new ScoringRiskProperties.WeightProfile(new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("10"));
            case "financial" -> new ScoringRiskProperties.WeightProfile(new BigDecimal("30"), new BigDecimal("25"), new BigDecimal("25"), new BigDecimal("10"), new BigDecimal("10"));
            case "cyclical" -> new ScoringRiskProperties.WeightProfile(new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("25"), new BigDecimal("15"), new BigDecimal("10"));
            default -> new ScoringRiskProperties.WeightProfile(new BigDecimal("30"), new BigDecimal("25"), new BigDecimal("20"), new BigDecimal("15"), new BigDecimal("10"));
        });
    }

    @Test
    void compute_knownInputs_allSubscoresCorrect() {
        // MoS = 22% → mosScore = 20; ROIC = 18% → qualityScore = 25
        // D/E = 0.8 (≤ 1.0) → safetyScore = 14; revenue +8% → growthScore = 10; no dividends → 0
        // total = 20 + 25 + 14 + 10 + 0 = 69
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(new BigDecimal("22"));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(valuation));

        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setRoic(new BigDecimal("0.18"));
        ratio.setDebtToEquity(new BigDecimal("0.8"));
        ratio.setDividendYield(new BigDecimal("0.006"));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio));

        FundamentalSnapshot year1 = new FundamentalSnapshot();
        year1.setRevenue(new BigDecimal("108000000000"));
        FundamentalSnapshot year2 = new FundamentalSnapshot();
        year2.setRevenue(new BigDecimal("100000000000"));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of(year1, year2));

        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        assertThat(result.getMosScore()).isEqualByComparingTo(new BigDecimal("20"));
        assertThat(result.getQualityScore()).isEqualByComparingTo(new BigDecimal("25"));
        assertThat(result.getSafetyScore()).isEqualByComparingTo(new BigDecimal("14"));
        assertThat(result.getGrowthScore()).isEqualByComparingTo(new BigDecimal("10"));
        assertThat(result.getDividendScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalScore()).isEqualByComparingTo(new BigDecimal("69"));
        assertThat(result.getRawTotalScore()).isEqualByComparingTo(new BigDecimal("69"));
        assertThat(result.isMosGateApplied()).isFalse();
        assertThat(result.getWeightProfile()).isEqualTo("dividend-paying");
    }

    @Test
    void compute_nullRoic_fallsBackToRoe() {
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(new BigDecimal("22"));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(valuation));

        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setRoic(null);
        ratio.setRoe(new BigDecimal("0.12")); // ROE 12% → qualityScore = 18
        ratio.setDebtToEquity(new BigDecimal("0.4")); // D/E ≤ 0.5 → 20
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio));

        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of());
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        assertThat(result.getQualityScore()).isEqualByComparingTo(new BigDecimal("21.60"));
        assertThat(result.getSafetyScore()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void compute_currentRatioBonusCapAt20() {
        // D/E ≤ 0.5 → base 20; currentRatio ≥ 2.0 → +2 but capped at 20
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(BigDecimal.ZERO);
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(valuation));

        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setDebtToEquity(new BigDecimal("0.3"));
        ratio.setCurrentRatio(new BigDecimal("3.0"));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio));

        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of());
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        assertThat(result.getSafetyScore()).isEqualByComparingTo(new BigDecimal("20"));
    }

    @Test
    void compute_highDividendAndStreak_dividendScore10() {
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(new BigDecimal("5"));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(valuation));

        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setDividendYield(new BigDecimal("0.03")); // 3% ≥ 2%
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio));

        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of());

        int year = LocalDate.now().getYear();
        List<DividendRecord> dividends = List.of(
                dividendRecord(year), dividendRecord(year - 1), dividendRecord(year - 2),
                dividendRecord(year - 3), dividendRecord(year - 4), dividendRecord(year - 5));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(dividends);

        ValueScore result = service.compute("AAPL");

        assertThat(result.getDividendScore()).isEqualByComparingTo(new BigDecimal("10"));
    }

    @Test
    void compute_nullValuationAndRatio_allZeroExceptGrowth() {
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.empty());
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of());

        FundamentalSnapshot year1 = new FundamentalSnapshot();
        year1.setRevenue(new BigDecimal("120"));
        FundamentalSnapshot year2 = new FundamentalSnapshot();
        year2.setRevenue(new BigDecimal("100"));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of(year1, year2));

        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        assertThat(result.getMosScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getQualityScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getSafetyScore()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getGrowthScore()).isEqualByComparingTo(new BigDecimal("20.00")); // +20% growth, non-dividend profile
        assertThat(result.getDividendScore()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void compute_negativeMarginOfSafety_capsTotalScoreAt40() {
        ValuationResult valuation = new ValuationResult();
        valuation.setMarginOfSafety(new BigDecimal("-10"));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.of(valuation));

        RatioSnapshot ratio = new RatioSnapshot();
        ratio.setRoic(new BigDecimal("0.18"));
        ratio.setDebtToEquity(new BigDecimal("0.3"));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ratio));

        FundamentalSnapshot year1 = new FundamentalSnapshot();
        year1.setRevenue(new BigDecimal("120"));
        FundamentalSnapshot year2 = new FundamentalSnapshot();
        year2.setRevenue(new BigDecimal("100"));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of(year1, year2));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        assertThat(result.getRawTotalScore()).isGreaterThan(new BigDecimal("40"));
        assertThat(result.getTotalScore()).isEqualByComparingTo(new BigDecimal("40"));
        assertThat(result.isMosGateApplied()).isTrue();
    }

    // RM2 (specs/sector-aware-valuation-metrics.md): REIT branch — all five pillars use
    // sector-aware metrics instead of GAAP formulas. Hand-computed expected values, same style as
    // compute_knownInputs_allSubscoresCorrect above.
    @Test
    void compute_reitSecurity_allFivePillarsUseReitMetrics() {
        security.setSector("REIT - Retail");
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.empty());

        RatioSnapshot ttmRatio = new RatioSnapshot();
        ttmRatio.setNetDebtToEbitda(new BigDecimal("5.0"));   // ≤ 5.5 conservative → safety base 20
        ttmRatio.setAffoPayoutRatio(new BigDecimal("0.80"));  // ≤ 0.85 conservative → dividend 10
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ttmRatio));

        RatioSnapshot annualRatioYear1 = new RatioSnapshot();
        annualRatioYear1.setFfoPerShare(new BigDecimal("3.00"));
        RatioSnapshot annualRatioYear2 = new RatioSnapshot();
        annualRatioYear2.setFfoPerShare(new BigDecimal("2.70")); // growth ≈ 11.1% → ≥10% → growth 15
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(annualRatioYear1, annualRatioYear2));

        FundamentalSnapshot latestAnnual = new FundamentalSnapshot();
        latestAnnual.setNetIncome(new BigDecimal("40"));
        latestAnnual.setDepreciationAndAmortization(new BigDecimal("60"));
        latestAnnual.setRevenue(new BigDecimal("200")); // FFO margin = 100/200 = 50% → ≥50% high → quality 25
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of(latestAnnual));

        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        ValuationBandResult pFfoBand = new ValuationBandResult();
        pFfoBand.setMetric("P_FFO");
        pFfoBand.setCurrentPercentile(new BigDecimal("20")); // ≤ 25th percentile → mos 30
        pFfoBand.setPosition(ValuationBandPosition.HISTORICALLY_CHEAP);
        when(valuationHistoryService.compute(security)).thenReturn(List.of(pFfoBand));

        ValueScore result = service.compute("AAPL");

        // weightProfile = "reit-utility" (mos 30, quality 20, safety 30, growth 10, dividend 10)
        assertThat(result.getWeightProfile()).isEqualTo("reit-utility");
        assertThat(result.getMosScore()).isEqualByComparingTo(new BigDecimal("30"));       // 30*30/30
        assertThat(result.getQualityScore()).isEqualByComparingTo(new BigDecimal("20.00")); // 25*20/25
        assertThat(result.getSafetyScore()).isEqualByComparingTo(new BigDecimal("30.00"));  // 20*30/20
        assertThat(result.getGrowthScore()).isEqualByComparingTo(new BigDecimal("10.00"));  // 15*10/15
        assertThat(result.getDividendScore()).isEqualByComparingTo(new BigDecimal("10.00")); // 10*10/10
        assertThat(result.getRawTotalScore()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(result.getTotalScore()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // RM2 REIT Safety +2 interest-coverage bonus, capped at 20 — same shape as
    // compute_currentRatioBonusCapAt20's GAAP-branch bonus test above.
    @Test
    void compute_reitSecurity_interestCoverageBonusCapAt20() {
        security.setSector("REIT");
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.empty());

        RatioSnapshot ttmRatio = new RatioSnapshot();
        ttmRatio.setNetDebtToEbitda(new BigDecimal("6.0"));          // ≤ 6.5 moderate → base 14
        ttmRatio.setInterestCoverageEbitda(new BigDecimal("5.0"));   // ≥ 3.0 → +2 → 16
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of(ttmRatio));
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of());
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of());
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());
        when(valuationHistoryService.compute(security)).thenReturn(List.of());

        ValueScore result = service.compute("AAPL");

        // base 14 (moderate tier) + 2 bonus = 16; scaled to weight-profile safety max 30: 16*30/20=24.00
        assertThat(result.getSafetyScore()).isEqualByComparingTo(new BigDecimal("24.00"));
    }

    // RM2: a non-REIT security must never call ValuationHistoryService — confirms the REIT branch
    // is additive and does not touch every other sector's formulas or introduce a new dependency
    // call on their read path.
    @Test
    void compute_nonReitSecurity_neverCallsValuationHistoryService() {
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security))
                .thenReturn(Optional.empty());
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(List.of());
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(
                security, Period.ANNUAL)).thenReturn(List.of());
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security))
                .thenReturn(List.of());

        service.compute("AAPL"); // security.sector == "Technology", set in setUp()

        Mockito.verifyNoInteractions(valuationHistoryService);
    }

    @Test
    void compute_symbolNotFound_throwsException() {
        when(securityRepository.findBySymbol("UNKNOWN")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.compute("UNKNOWN"))
                .isInstanceOf(SymbolNotFoundException.class);
    }

    private DividendRecord dividendRecord(int year) {
        DividendRecord r = new DividendRecord();
        r.setExDividendDate(LocalDate.of(year, 6, 15));
        r.setAmount(BigDecimal.ONE);
        return r;
    }
}
