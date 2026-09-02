package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import it.mazzoni.vis.domain.entity.AltmanFormulaVariant;
import it.mazzoni.vis.domain.entity.AltmanResult;
import it.mazzoni.vis.domain.entity.AltmanZone;
import it.mazzoni.vis.domain.entity.CapitalAllocationResult;
import it.mazzoni.vis.domain.entity.CapitalAllocatorClassification;
import it.mazzoni.vis.domain.entity.CyclicalityClassification;
import it.mazzoni.vis.domain.entity.CyclicalityResult;
import it.mazzoni.vis.domain.entity.EarningsQualityClassification;
import it.mazzoni.vis.domain.entity.EarningsQualityResult;
import it.mazzoni.vis.domain.entity.MoatResult;
import it.mazzoni.vis.domain.entity.MoatStrength;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PiotroskiResult;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.RiskAvailabilityStatus;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.RoicTrend;
import it.mazzoni.vis.domain.entity.SharesOutstandingTrend;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.WaccResultEntity;
import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.AltmanResultRepository;
import it.mazzoni.vis.domain.repository.CyclicalityResultRepository;
import it.mazzoni.vis.domain.repository.EarningsQualityResultRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.GrahamChecklistItemRepository;
import it.mazzoni.vis.domain.repository.PiotroskiResultRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.StabilityResultRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.domain.repository.WaccResultRepository;
import it.mazzoni.vis.security.domain.AnalystEstimateRepository;
import it.mazzoni.vis.security.dto.SecurityReviewResponse;
import it.mazzoni.vis.moat.CapitalAllocationService;
import it.mazzoni.vis.moat.MoatAssessmentService;
import it.mazzoni.vis.moat.StabilityService;
import it.mazzoni.vis.moat.ValuationHistoryService;
import it.mazzoni.vis.scoring.RiskAnalysisService;
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
    @Mock PiotroskiResultRepository piotroskiResultRepository;
    @Mock AltmanResultRepository altmanResultRepository;
    @Mock CyclicalityResultRepository cyclicalityResultRepository;
    @Mock EarningsQualityResultRepository earningsQualityResultRepository;
    @Mock WaccResultRepository waccResultRepository;
    @Mock GrahamChecklistItemRepository grahamChecklistItemRepository;
    @Mock StabilityResultRepository stabilityResultRepository;
    @Mock AnalystEstimateRepository analystEstimateRepository;
    @Mock MoatAssessmentService moatAssessmentService;
    @Mock CapitalAllocationService capitalAllocationService;
    @Mock StabilityService stabilityService;
    @Mock ValuationHistoryService valuationHistoryService;
    @Mock RiskAnalysisService riskAnalysisService;

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
                piotroskiResultRepository,
                altmanResultRepository,
                cyclicalityResultRepository,
                earningsQualityResultRepository,
                waccResultRepository,
                grahamChecklistItemRepository,
                stabilityResultRepository,
                analystEstimateRepository,
                new DividendsService(),
                new GrowthService(),
                moatAssessmentService,
                capitalAllocationService,
                stabilityService,
                valuationHistoryService,
                riskAnalysisService
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
        when(riskAnalysisService.computePiotroski("AAPL")).thenReturn(piotroski(security));
        when(riskAnalysisService.computeAltman("AAPL")).thenReturn(altman(security));
        when(riskAnalysisService.assessCyclicality("AAPL")).thenReturn(cyclicality(security));
        when(riskAnalysisService.computeEarningsQuality("AAPL")).thenReturn(earningsQuality(security));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("AAPL")).thenReturn(List.of());
        when(securityRepository.findByActiveTrueAndSectorAndSymbolNot("Technology", "AAPL")).thenReturn(List.of());
        when(moatAssessmentService.analyze(security)).thenReturn(moat(security));
        when(stabilityResultRepository.findBySecurityAndResultDateOrderByCriterionCodeAsc(any(Security.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(capitalAllocationService.analyze(security)).thenReturn(capitalAllocation(security));
        when(valuationHistoryService.compute(security)).thenReturn(List.of());

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
        assertThat(response.piotroski()).isNotNull();
        assertThat(response.altman()).isNotNull();
        assertThat(response.cyclicality()).isNotNull();
        assertThat(response.earningsQuality()).isNotNull();
        assertThat(response.moat()).isNotNull();
        assertThat(response.capitalAllocation()).isNotNull();
        assertThat(response.valuationBands()).isNotNull();
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
        when(riskAnalysisService.computePiotroski("PG")).thenReturn(piotroski(security));
        when(riskAnalysisService.computeAltman("PG")).thenReturn(altman(security));
        when(riskAnalysisService.assessCyclicality("PG")).thenReturn(cyclicality(security));
        when(riskAnalysisService.computeEarningsQuality("PG")).thenReturn(earningsQuality(security));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc("PG")).thenReturn(List.of());
        when(securityRepository.findByActiveTrueAndSectorAndSymbolNot("Technology", "AAPL")).thenReturn(List.of());
        when(moatAssessmentService.analyze(security)).thenReturn(moat(security));
        when(stabilityResultRepository.findBySecurityAndResultDateOrderByCriterionCodeAsc(any(Security.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(capitalAllocationService.analyze(security)).thenReturn(capitalAllocation(security));
        when(valuationHistoryService.compute(security)).thenReturn(List.of());

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

    // RM3 (specs/2026-09-02-rm3-screener-security-detail-surfacing/) — sectorMetrics surfacing.

    @Test
    void getReview_reitSecurity_returnsPopulatedSectorMetrics() {
        Security security = security("O", "Real Estate");
        RatioSnapshot ratios = ratios(security);
        ratios.setFfoPerShare(new BigDecimal("3.9024"));
        ratios.setAffoPerShare(new BigDecimal("1.9897"));
        ratios.setPriceToFfo(new BigDecimal("15.6442"));
        ratios.setPriceToAffo(new BigDecimal("30.6830"));
        ratios.setNetDebtToEbitda(new BigDecimal("9.1298"));
        ratios.setInterestCoverageEbitda(new BigDecimal("3.1088"));
        ratios.setAffoPayoutRatio(new BigDecimal("1.6225"));
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("O");

        assertThat(response.sectorMetrics()).isNotNull();
        assertThat(response.sectorMetrics().ffoPerShare()).isEqualByComparingTo("3.9024");
        assertThat(response.sectorMetrics().affoPerShare()).isEqualByComparingTo("1.9897");
        assertThat(response.sectorMetrics().priceToFfo()).isEqualByComparingTo("15.6442");
        assertThat(response.sectorMetrics().priceToAffo()).isEqualByComparingTo("30.6830");
        assertThat(response.sectorMetrics().netDebtToEbitda()).isEqualByComparingTo("9.1298");
        assertThat(response.sectorMetrics().interestCoverageEbitda()).isEqualByComparingTo("3.1088");
        assertThat(response.sectorMetrics().affoPayoutRatio()).isEqualByComparingTo("1.6225");
        assertThat(response.sectorMetrics().availability().status().name()).isEqualTo("AVAILABLE");
    }

    @Test
    void getReview_reitSecurityMissingSectorMetrics_returnsInsufficientData() {
        // A REIT fixture whose RatioSnapshot has all 7 RM2 fields null — e.g. seeded before
        // RM2's ordering fix — must surface as an explicit MISSING_INTERNAL_COMPUTATION status,
        // never a silently-zeroed sectorMetrics object (Design Principle 12).
        Security security = security("O", "Real Estate");
        RatioSnapshot ratios = ratios(security);
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("O");

        assertThat(response.sectorMetrics()).isNotNull();
        assertThat(response.sectorMetrics().ffoPerShare()).isNull();
        assertThat(response.sectorMetrics().availability().status().name())
                .isEqualTo("MISSING_INTERNAL_COMPUTATION");
    }

    @Test
    void getReview_nonReitSecurity_sectorMetricsIsNull() {
        Security security = security();
        RatioSnapshot ratios = ratios(security);
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("AAPL");

        assertThat(response.sectorMetrics()).isNull();
    }

    @Test
    void getReview_reitSecurity_dataQualityNotesUsesSpecificCaveat() {
        Security security = security("O", "Real Estate");
        RatioSnapshot ratios = ratios(security);
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("O");

        assertThat(response.dataQualityNotes())
                .anySatisfy(note -> assertThat(note.message()).contains("Moat and Business Quality section"));
        assertThat(response.dataQualityNotes())
                .noneMatch(note -> SectorClassifier.REIT_UTILITY_METRIC_CAVEAT.equals(note.message()));
    }

    @Test
    void getReview_utilitySecurity_dataQualityNotesKeepsGenericCaveat() {
        Security security = security("NEE", "Utilities");
        RatioSnapshot ratios = ratios(security);
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("NEE");

        assertThat(response.sectorMetrics()).isNull();
        assertThat(response.dataQualityNotes())
                .anyMatch(note -> SectorClassifier.REIT_UTILITY_METRIC_CAVEAT.equals(note.message()));
    }

    @Test
    void getReview_nonReitOrUtility_noSectorMetricNote() {
        Security security = security();
        RatioSnapshot ratios = ratios(security);
        stubMinimalReview(security, ratios);

        SecurityReviewResponse response = service.getReview("AAPL");

        assertThat(response.dataQualityNotes()).noneMatch(note -> "Sector metrics".equals(note.category()));
    }

    /** Stubs the full mock chain {@code getReview} needs to complete without NPEs, parameterized
     * by security/sector — mirrors {@code getReview_knownSymbol_returnsNestedReviewPacket}'s stub
     * set exactly, generalized for the RM3 sector-metric tests above. */
    private void stubMinimalReview(Security security, RatioSnapshot ratios) {
        String symbol = security.getSymbol();
        FundamentalSnapshot annual = annual(security);
        PriceQuote quote = quote(security);
        ValuationResult valuation = valuation(security);
        ValueScore score = score(security);

        when(securityRepository.findBySymbol(symbol)).thenReturn(Optional.of(security));
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
        when(ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.of(ratios));
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        when(waccResultRepository.findByValuationResult(valuation)).thenReturn(Optional.empty());
        when(grahamChecklistItemRepository.findByValuationResultOrderByCriterionCodeAsc(valuation)).thenReturn(List.of());
        when(valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.of(score));
        when(riskAnalysisService.computePiotroski(symbol)).thenReturn(piotroski(security));
        when(riskAnalysisService.computeAltman(symbol)).thenReturn(altman(security));
        when(riskAnalysisService.assessCyclicality(symbol)).thenReturn(cyclicality(security));
        when(riskAnalysisService.computeEarningsQuality(symbol)).thenReturn(earningsQuality(security));
        when(dividendRecordRepository.findBySecurityOrderByExDividendDateDesc(security)).thenReturn(List.of());
        when(analystEstimateRepository.findBySecuritySymbolOrderByTargetDateDesc(symbol)).thenReturn(List.of());
        when(securityRepository.findByActiveTrueAndSectorAndSymbolNot(security.getSector(), symbol)).thenReturn(List.of());
        when(moatAssessmentService.analyze(security)).thenReturn(moat(security));
        when(stabilityResultRepository.findBySecurityAndResultDateOrderByCriterionCodeAsc(any(Security.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(capitalAllocationService.analyze(security)).thenReturn(capitalAllocation(security));
        when(valuationHistoryService.compute(security)).thenReturn(List.of());
    }

    private Security security(String symbol, String sector) {
        Security s = new Security();
        s.setSymbol(symbol);
        s.setCompanyName(symbol + " Inc.");
        s.setSector(sector);
        s.setCurrency("USD");
        s.setExchange("NYSE");
        s.setCountry("US");
        return s;
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

    private PiotroskiResult piotroski(Security security) {
        PiotroskiResult result = new PiotroskiResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setTotalScore(7);
        result.setPositiveNetIncome(true);
        result.setPositiveOperatingCashFlow(true);
        result.setImprovingRoa(true);
        result.setCashFlowQuality(true);
        result.setLowerLeverage(true);
        result.setImprovingCurrentRatio(true);
        result.setNoShareDilution(true);
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return result;
    }

    private AltmanResult altman(Security security) {
        AltmanResult result = new AltmanResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setScore(new BigDecimal("4.20"));
        result.setZone(AltmanZone.SAFE);
        result.setFormulaVariant(AltmanFormulaVariant.NON_MANUFACTURING);
        result.setWorkingCapitalToAssets(new BigDecimal("0.20"));
        result.setRetainedEarningsToAssets(new BigDecimal("0.15"));
        result.setEbitToAssets(new BigDecimal("0.12"));
        result.setMarketValueEquityToLiabilities(new BigDecimal("3.00"));
        result.setSalesToAssets(new BigDecimal("1.10"));
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return result;
    }

    private CyclicalityResult cyclicality(Security security) {
        CyclicalityResult result = new CyclicalityResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setClassification(CyclicalityClassification.STABLE);
        result.setYearsAnalyzed(10);
        result.setRevenueCoefficient(new BigDecimal("0.08"));
        result.setEarningsCoefficient(new BigDecimal("0.12"));
        result.setNormalizedEarnings(new BigDecimal("200.00"));
        result.setCycleAdjustedPe(new BigDecimal("15.00"));
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return result;
    }

    private EarningsQualityResult earningsQuality(Security security) {
        EarningsQualityResult result = new EarningsQualityResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setClassification(EarningsQualityClassification.STRONG);
        result.setFcfToNetIncome(new BigDecimal("1.10"));
        result.setSloanAccrualsRatio(new BigDecimal("-0.02"));
        result.setYearsAnalyzed(5);
        result.setDeteriorating(false);
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return result;
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

    private MoatResult moat(Security security) {
        MoatResult result = new MoatResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setMoatStrength(MoatStrength.NARROW);
        result.setRoicTrend(RoicTrend.STABLE);
        result.setYearsAnalyzed(10);
        result.setYearsRoicAboveWacc(7);
        return result;
    }

    private CapitalAllocationResult capitalAllocation(Security security) {
        CapitalAllocationResult result = new CapitalAllocationResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setSharesOutstandingTrend(SharesOutstandingTrend.STABLE);
        result.setClassification(CapitalAllocatorClassification.STABLE);
        result.setYearsAnalyzed(10);
        return result;
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
