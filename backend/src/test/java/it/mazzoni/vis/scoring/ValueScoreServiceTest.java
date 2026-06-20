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

    ValueScoreService service;
    Security security;

    @BeforeEach
    void setUp() {
        service = new ValueScoreService(securityRepository, valuationResultRepository,
                ratioSnapshotRepository, fundamentalSnapshotRepository,
                dividendRecordRepository, valueScoreRepository);
        security = new Security();
        security.setSymbol("AAPL");
        Mockito.lenient().when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        Mockito.lenient().when(valueScoreRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
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

        assertThat(result.getQualityScore()).isEqualByComparingTo(new BigDecimal("18"));
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
        assertThat(result.getGrowthScore()).isEqualByComparingTo(new BigDecimal("15")); // +20% growth
        assertThat(result.getDividendScore()).isEqualByComparingTo(BigDecimal.ZERO);
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
