package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.WaccResultEntity;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.GrahamChecklistItemRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.domain.repository.WaccResultRepository;
import it.mazzoni.vis.security.domain.AnalystEstimateRepository;
import it.mazzoni.vis.security.dto.SecurityReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityReviewServiceTest {

    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;
    @Mock ValuationResultRepository valuationResultRepository;
    @Mock DividendRecordRepository dividendRecordRepository;
    @Mock ValueScoreRepository valueScoreRepository;
    @Mock WaccResultRepository waccResultRepository;
    @Mock GrahamChecklistItemRepository grahamChecklistItemRepository;
    @Mock AnalystEstimateRepository analystEstimateRepository;

    SecurityReviewService service;

    @BeforeEach
    void setUp() {
        service = new SecurityReviewService(
                securityRepository,
                fundamentalSnapshotRepository,
                ratioSnapshotRepository,
                priceQuoteRepository,
                valuationResultRepository,
                dividendRecordRepository,
                valueScoreRepository,
                waccResultRepository,
                grahamChecklistItemRepository,
                analystEstimateRepository,
                new DividendsService(),
                new GrowthService()
        );
    }

    @Test
    void getReview_knownSymbol_returnsNestedReviewPacket() {
        Security security = security();
        FundamentalSnapshot annual = annual(security);
        RatioSnapshot ratios = ratios(security);
        PriceQuote quote = quote(security);
        ValuationResult valuation = valuation(security);
        ValueScore score = score(security);

        when(securityRepository.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(Optional.of(annual));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(annual));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.QUARTERLY))
                .thenReturn(List.of());
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(Optional.empty());
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(ratios));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(any(Security.class))).thenReturn(Optional.of(ratios));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        when(waccResultRepository.findByValuationResult(valuation)).thenReturn(Optional.of(wacc(valuation)));
        when(grahamChecklistItemRepository.findByValuationResultOrderByCriterionCodeAsc(valuation))
                .thenReturn(List.of(checklistItem(valuation, "PE_RATIO", "P/E < 15", "FAIL", "20.00")));
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.of(score));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("AAPL")).thenReturn(List.of());
        when(securityRepository.findBySectorAndSymbolNot("Technology", "AAPL")).thenReturn(List.of());

        SecurityReviewResponse response = service.getReview("aapl");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.detail().companyName()).isEqualTo("Apple Inc.");
        assertThat(response.financials().annuals()).hasSize(1);
        assertThat(response.ratios().ratios()).hasSize(1);
        assertThat(response.valuation()).isNotNull();
        assertThat(response.valuation().wacc()).isNotNull();
        assertThat(response.valuation().wacc().fallbackUsed()).isTrue();
        assertThat(response.valuation().grahamChecklist()).isNotNull();
        assertThat(response.valuation().grahamChecklist().failed()).isEqualTo(1);
        assertThat(response.score()).isNotNull();
        assertThat(response.financialHealth().currentRatio()).isEqualByComparingTo("1.25");
        assertThat(response.sourceCoverage()).extracting(SecurityReviewResponse.SourceCoverageItem::category)
                .contains("Profile", "Fundamentals", "Ratios", "Quote", "Valuation", "Score");
        assertThat(response.freshness()).extracting(SecurityReviewResponse.FreshnessItem::category)
                .contains("Fundamentals", "Ratios", "Quote", "Valuation", "Score");
        assertThat(response.dataQualityNotes()).extracting(SecurityReviewResponse.DataQualityNote::category)
                .contains("Advice boundary");
    }

    @Test
    void getReview_missingScoreAndGuardedDcf_returnsExplicitAvailabilityStates() {
        Security security = security();
        FundamentalSnapshot annual = annual(security);
        RatioSnapshot ratios = ratios(security);
        PriceQuote quote = quote(security);
        ValuationResult valuation = valuation(security);
        valuation.setDcfFairValue(null);

        when(securityRepository.findBySymbol("PG")).thenReturn(Optional.of(security));
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(Optional.of(annual));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL))
                .thenReturn(List.of(annual));
        when(fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.QUARTERLY))
                .thenReturn(List.of());
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM))
                .thenReturn(Optional.empty());
        when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL))
                .thenReturn(List.of(ratios));
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(any(Security.class))).thenReturn(Optional.of(ratios));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        when(waccResultRepository.findByValuationResult(valuation)).thenReturn(Optional.empty());
        when(grahamChecklistItemRepository.findByValuationResultOrderByCriterionCodeAsc(valuation)).thenReturn(List.of());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.empty());
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("PG")).thenReturn(List.of());
        when(securityRepository.findBySectorAndSymbolNot("Technology", "AAPL")).thenReturn(List.of());

        SecurityReviewResponse response = service.getReview("pg");

        assertThat(response.availability()).anySatisfy(item -> {
            assertThat(item.category()).isEqualTo("Valuation");
            assertThat(item.state().status().name()).isEqualTo("GUARDRAIL_BLOCKED");
            assertThat(item.state().reason()).contains("eligibility guardrails");
        });
        assertThat(response.availability()).anySatisfy(item -> {
            assertThat(item.category()).isEqualTo("Score");
            assertThat(item.state().status().name()).isEqualTo("MISSING_INTERNAL_COMPUTATION");
            assertThat(item.state().reason()).contains("No persisted value score");
        });
        assertThat(response.availability()).anySatisfy(item -> {
            assertThat(item.category()).isEqualTo("Dividends");
            assertThat(item.state().status().name()).isEqualTo("PROVIDER_LIMITED");
        });
        assertThat(response.dataQualityNotes()).extracting(SecurityReviewResponse.DataQualityNote::category)
                .contains("Valuation", "Score", "Dividends", "Advice boundary");
    }

    private Security security() {
        Security s = new Security();
        s.setSymbol("AAPL");
        s.setCompanyName("Apple Inc.");
        s.setSector("Technology");
        s.setCurrency("USD");
        s.setExchange("NASDAQ");
        s.setCountry("US");
        return s;
    }

    private FundamentalSnapshot annual(Security security) {
        FundamentalSnapshot f = new FundamentalSnapshot();
        f.setSecurity(security);
        f.setPeriod(Period.ANNUAL);
        f.setFiscalYear(LocalDate.now().getYear());
        f.setReportDate(LocalDate.now());
        f.setRevenue(new BigDecimal("1000.00"));
        f.setNetIncome(new BigDecimal("200.00"));
        f.setFreeCashFlow(new BigDecimal("150.00"));
        f.setEps(new BigDecimal("5.00"));
        f.setTotalEquity(new BigDecimal("500.00"));
        f.setTotalDebt(new BigDecimal("100.00"));
        f.setCash(new BigDecimal("40.00"));
        f.setSharesOutstanding(100L);
        return f;
    }

    private RatioSnapshot ratios(Security security) {
        RatioSnapshot r = new RatioSnapshot();
        r.setSecurity(security);
        r.setPeriod(Period.ANNUAL);
        r.setReportDate(LocalDate.now());
        r.setPeRatio(new BigDecimal("20.00"));
        r.setRoic(new BigDecimal("18.00"));
        r.setRoe(new BigDecimal("25.00"));
        r.setDebtToEquity(new BigDecimal("0.20"));
        r.setCurrentRatio(new BigDecimal("1.25"));
        r.setDividendYield(new BigDecimal("0.50"));
        r.setGrossMargin(new BigDecimal("40.00"));
        return r;
    }

    private PriceQuote quote(Security security) {
        PriceQuote q = new PriceQuote();
        q.setSecurity(security);
        q.setQuoteDate(LocalDate.now());
        q.setClose(new BigDecimal("180.00"));
        return q;
    }

    private ValuationResult valuation(Security security) {
        ValuationResult v = new ValuationResult();
        v.setSecurity(security);
        v.setValuationDate(LocalDate.now());
        v.setCurrentPrice(new BigDecimal("180.00"));
        v.setDcfFairValue(new BigDecimal("210.00"));
        v.setDcfFairValueLow(new BigDecimal("190.00"));
        v.setDcfFairValueHigh(new BigDecimal("230.00"));
        v.setDcfTerminalValuePercentage(new BigDecimal("72.00"));
        v.setDcfHighTerminalDependence(true);
        v.setGrahamNumber(new BigDecimal("160.00"));
        v.setEpvFairValue(new BigDecimal("150.00"));
        v.setEpvNormalizedEarnings(new BigDecimal("12000.00"));
        v.setEpvYearsAveraged(5);
        v.setOwnerEarnings(new BigDecimal("175.00"));
        v.setMaintenanceCapexEstimate(new BigDecimal("25.00"));
        v.setCompositeFairValue(new BigDecimal("205.00"));
        v.setMarginOfSafety(new BigDecimal("13.89"));
        v.setRecommendation(Recommendation.QUALITY_VALUE);
        v.setSource("FMP");
        return v;
    }

    private ValueScore score(Security security) {
        ValueScore s = new ValueScore();
        s.setSecurity(security);
        s.setScoreDate(LocalDate.now());
        s.setTotalScore(new BigDecimal("72.50"));
        return s;
    }

    private WaccResultEntity wacc(ValuationResult valuation) {
        WaccResultEntity w = new WaccResultEntity();
        w.setValuationResult(valuation);
        w.setWacc(new BigDecimal("0.090000"));
        w.setRiskFreeRate(new BigDecimal("0.040000"));
        w.setEquityRiskPremium(new BigDecimal("0.055000"));
        w.setBeta(new BigDecimal("1.000000"));
        w.setCostOfEquity(new BigDecimal("0.095000"));
        w.setCostOfDebt(new BigDecimal("0.050000"));
        w.setDebtWeight(new BigDecimal("0.200000"));
        w.setEquityWeight(new BigDecimal("0.800000"));
        w.setEffectiveTaxRate(new BigDecimal("0.210000"));
        w.setFallbackUsed(true);
        w.setSource("sector-median");
        return w;
    }

    private GrahamChecklistItem checklistItem(ValuationResult valuation, String code, String label, String status, String actualValue) {
        GrahamChecklistItem item = new GrahamChecklistItem();
        item.setValuationResult(valuation);
        item.setCriterionCode(code);
        item.setLabel(label);
        item.setStatus(status);
        item.setActualValue(new BigDecimal(actualValue));
        return item;
    }
}
