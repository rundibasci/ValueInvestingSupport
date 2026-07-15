package it.mazzoni.vis.admin;

import it.mazzoni.vis.config.ValuationDefaultsProperties;
import it.mazzoni.vis.marketdata.SourceTracker;
import it.mazzoni.vis.domain.CompanyProfile;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.MarketPriceQuote;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.InsiderTradeRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpDividendEntry;
import it.mazzoni.vis.marketdata.fmp.dto.FmpInsiderTradingEntry;
import it.mazzoni.vis.moat.CapitalAllocationService;
import it.mazzoni.vis.moat.MoatAssessmentService;
import it.mazzoni.vis.scoring.RiskAnalysisService;
import it.mazzoni.vis.scoring.ValueScoreService;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationNotApplicableException;
import it.mazzoni.vis.valuation.ValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedServiceTest {

    @Mock MarketDataClient marketDataClient;
    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;
    @Mock DividendRecordRepository dividendRecordRepository;
    @Mock InsiderTradeRepository insiderTradeRepository;
    @Mock ValuationService valuationService;
    @Mock ValueScoreService valueScoreService;
    @Mock RiskAnalysisService riskAnalysisService;
    @Mock MoatAssessmentService moatAssessmentService;
    @Mock CapitalAllocationService capitalAllocationService;
    @Mock SourceTracker sourceTracker;

    SeedTickerService seedTickerService;
    SeedService seedService;

    @BeforeEach
    void setUp() {
        ValuationDefaultsProperties defaults = new ValuationDefaultsProperties(
                new BigDecimal("0.09"), new BigDecimal("0.08"),
                new BigDecimal("0.04"), new BigDecimal("0.025"));
        seedTickerService = new SeedTickerService(marketDataClient, securityRepository,
                fundamentalSnapshotRepository, ratioSnapshotRepository,
                priceQuoteRepository, dividendRecordRepository, insiderTradeRepository,
                valuationService, valueScoreService, riskAnalysisService, moatAssessmentService,
                capitalAllocationService, defaults, sourceTracker);
        seedService = new SeedService(seedTickerService);

        // Shared lenient stubs — save always returns the argument, exists-checks default to false.
        Mockito.lenient().when(securityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(fundamentalSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(priceQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(dividendRecordRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(dividendRecordRepository.findBySecurityAndExDividendDate(
                any(), any())).thenReturn(Optional.empty());
        Mockito.lenient().when(insiderTradeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(insiderTradeRepository.existsBySecurityAndTradeDateAndInsiderName(
                any(), any(), any())).thenReturn(false);
        Mockito.lenient().when(fundamentalSnapshotRepository.existsBySecurityAndPeriodAndReportDate(
                any(), any(), any())).thenReturn(false);
        Mockito.lenient().when(ratioSnapshotRepository.existsBySecurityAndPeriodAndReportDate(
                any(), any(), any())).thenReturn(false);
        Mockito.lenient().when(ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(
                any(), any())).thenReturn(List.of());
        Mockito.lenient().when(priceQuoteRepository.existsBySecurityAndQuoteDate(
                any(), any())).thenReturn(false);
    }

    @Test
    void seedTickers_happyPath_returnsSuccessResultsForAllTickers() {
        stubFmpData("AAPL", "Apple Inc.");
        stubFmpData("KO", "Coca-Cola Co.");
        stubValuation("AAPL", new BigDecimal("210.50"), new BigDecimal("13.60"), Recommendation.QUALITY_VALUE);
        stubValuation("KO", new BigDecimal("58.20"), new BigDecimal("18.40"), Recommendation.QUALITY_VALUE);

        List<SeedResult> results = seedService.seedTickers(List.of("AAPL", "KO"));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).symbol()).isEqualTo("AAPL");
        assertThat(results.get(0).compositeFairValue()).isEqualByComparingTo("210.50");
        assertThat(results.get(0).totalScore()).isEqualByComparingTo("66.00");
        assertThat(results.get(0).description()).isEqualTo("Apple Inc. makes ingredient solutions.");
        assertThat(results.get(0).error()).isNull();
        assertThat(results.get(1).symbol()).isEqualTo("KO");
        assertThat(results.get(1).marginOfSafety()).isEqualByComparingTo("18.40");
        assertThat(results.get(1).error()).isNull();
        verify(riskAnalysisService).computePiotroski("AAPL");
        verify(riskAnalysisService).computeAltman("AAPL");
        verify(riskAnalysisService).assessCyclicality("AAPL");
        verify(riskAnalysisService).computeEarningsQuality("AAPL");
        verify(moatAssessmentService, times(2)).analyze(any(Security.class));
        verify(capitalAllocationService, times(2)).analyze(any(Security.class));
    }

    @Test
    void seedTickers_oneFmp404_returnsErrorForThatTickerOtherSucceeds() {
        when(marketDataClient.getProfile("XYZ"))
                .thenThrow(new MarketDataException(MarketDataException.ErrorCode.NOT_FOUND, "XYZ"));
        stubFmpData("AAPL", "Apple Inc.");
        stubValuation("AAPL", new BigDecimal("210.50"), new BigDecimal("13.60"), Recommendation.QUALITY_VALUE);

        List<SeedResult> results = seedService.seedTickers(List.of("XYZ", "AAPL"));

        assertThat(results).hasSize(2);
        SeedResult xyzResult = results.stream().filter(r -> r.symbol().equals("XYZ")).findFirst().orElseThrow();
        assertThat(xyzResult.error()).isEqualTo("not found");
        assertThat(xyzResult.compositeFairValue()).isNull();

        SeedResult aaplResult = results.stream().filter(r -> r.symbol().equals("AAPL")).findFirst().orElseThrow();
        assertThat(aaplResult.error()).isNull();
        assertThat(aaplResult.compositeFairValue()).isNotNull();
    }

    @Test
    void seedTickers_fmpServiceUnavailable_returnsErrorResult() {
        when(marketDataClient.getProfile("AAPL"))
                .thenThrow(new MarketDataException(MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, "AAPL"));

        List<SeedResult> results = seedService.seedTickers(List.of("AAPL"));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).error()).isNotNull();
        verify(valuationService, never()).calculate(any(), any());
        verify(valueScoreService, never()).compute(any());
    }

    @Test
    void seedTickers_runtimeFailureReturnsErrorAndContinuesWithNextTicker() {
        when(marketDataClient.getProfile("BROKEN"))
                .thenThrow(new IllegalStateException("valuation data incomplete"));
        stubFmpData("AAPL", "Apple Inc.");
        stubValuation("AAPL", new BigDecimal("210.50"), new BigDecimal("13.60"), Recommendation.QUALITY_VALUE);

        List<SeedResult> results = seedService.seedTickers(List.of("BROKEN", "AAPL"));

        assertThat(results).hasSize(2);
        SeedResult brokenResult = results.stream().filter(r -> r.symbol().equals("BROKEN")).findFirst().orElseThrow();
        assertThat(brokenResult.status()).isEqualTo("failed");
        assertThat(brokenResult.error()).isEqualTo("valuation data incomplete");

        SeedResult aaplResult = results.stream().filter(r -> r.symbol().equals("AAPL")).findFirst().orElseThrow();
        assertThat(aaplResult.error()).isNull();
        assertThat(aaplResult.status()).isEqualTo("seeded");
    }

    @Test
    void seedTickers_valuationNotApplicable_returnsPartialMarketDataWithoutFabricatedAnalytics() {
        stubFmpData("APD", "Air Products and Chemicals");
        when(valuationService.calculate(eq("APD"), any()))
                .thenThrow(new ValuationNotApplicableException("APD"));
        when(sourceTracker.summarize()).thenReturn("profile:fmp,fundamentals:yahoo,ratios:fmp,quote:fmp");

        SeedResult result = seedService.seedTickers(List.of("APD")).getFirst();

        assertThat(result.status()).isEqualTo("seeded_partial");
        assertThat(result.reasonCode()).isEqualTo("valuation_guardrail_blocked");
        assertThat(result.reason()).contains("Market data was saved");
        assertThat(result.companyName()).isEqualTo("Air Products and Chemicals");
        assertThat(result.source()).contains("yahoo");
        assertThat(result.fallbackReason()).contains("Yahoo Finance");
        assertThat(result.compositeFairValue()).isNull();
        assertThat(result.marginOfSafety()).isNull();
        assertThat(result.totalScore()).isNull();
        assertThat(result.recommendation()).isNull();
        assertThat(result.error()).isNull();
        verify(securityRepository).save(any(Security.class));
        verify(fundamentalSnapshotRepository, times(2)).save(any());
        verify(ratioSnapshotRepository, times(3)).save(any());
        verify(priceQuoteRepository).save(any());
        verify(valueScoreService, never()).compute("APD");
        verify(riskAnalysisService).computePiotroski("APD");
        verify(moatAssessmentService).analyze(any(Security.class));
    }

    @Test
    void seedTickers_existingCurrentFundamentalsReplacesCurrentRows() {
        stubFmpData("AAPL", "Apple Inc.");
        stubValuation("AAPL", new BigDecimal("200.00"), new BigDecimal("10.00"), Recommendation.QUALITY_VALUE);

        seedService.seedTickers(List.of("AAPL"));

        verify(fundamentalSnapshotRepository).deleteBySecurityAndPeriod(any(Security.class), eq(Period.ANNUAL));
        verify(fundamentalSnapshotRepository).deleteBySecurityAndPeriod(any(Security.class), eq(Period.TTM));
        verify(fundamentalSnapshotRepository, times(2)).save(any());
        verify(marketDataClient).getFundamentals("AAPL");
    }

    @Test
    void seedTickers_persistsProviderAnnualRatioHistory() {
        stubFmpData("AAPL", "Apple Inc.");
        stubValuation("AAPL", new BigDecimal("200.00"), new BigDecimal("10.00"), Recommendation.QUALITY_VALUE);

        seedService.seedTickers(List.of("AAPL"));

        verify(ratioSnapshotRepository).deleteBySecurityAndPeriod(any(Security.class), eq(Period.TTM));
        verify(ratioSnapshotRepository).deleteBySecurityAndPeriod(any(Security.class), eq(Period.ANNUAL));
        verify(marketDataClient).getAnnualRatios("AAPL");
        verify(ratioSnapshotRepository, times(3)).save(any());
    }

    @Test
    void seedTickers_persistsDividendHistoryForSeededSymbol() {
        stubFmpData("INGR", "Ingredion Incorporated");
        stubValuation("INGR", new BigDecimal("145.76"), new BigDecimal("32.25"), Recommendation.STRONG_BUY);

        seedService.seedTickers(List.of("INGR"));

        verify(marketDataClient).getDividendHistory("INGR");
        verify(dividendRecordRepository, times(1)).save(any());
    }

    @Test
    void seedTickers_persistsInsiderTradesForSeededSymbol() {
        stubFmpData("INGR", "Ingredion Incorporated");
        stubValuation("INGR", new BigDecimal("145.76"), new BigDecimal("32.25"), Recommendation.STRONG_BUY);

        seedService.seedTickers(List.of("INGR"));

        verify(marketDataClient).getInsiderTransactions("INGR");
        verify(insiderTradeRepository, times(1)).save(any());
    }

    private void stubFmpData(String symbol, String companyName) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(companyName);

        when(marketDataClient.getProfile(symbol)).thenReturn(
                new CompanyProfile(symbol, companyName, "Technology", "Consumer Electronics",
                        "US", "USD", "NASDAQ", new BigDecimal("2000000000000"),
                        companyName + " makes ingredient solutions.", "https://example.com"));
        when(securityRepository.findBySymbol(symbol)).thenReturn(Optional.of(security));
        Mockito.lenient().when(marketDataClient.getFundamentals(symbol)).thenReturn(
                new FundamentalSnapshot(symbol, companyName, "Technology", "Consumer Electronics",
                        "US", "USD", new BigDecimal("182.50"),
                        new BigDecimal("6.13"), null, 15550061000L,
                        List.of(new BigDecimal("394330000000")),
                        List.of(new BigDecimal("96995000000")),
                        List.of(new BigDecimal("111443000000")),
                        new BigDecimal("78075000000"),
                        new BigDecimal("108040000000"), new BigDecimal("29965000000")));
        Mockito.lenient().when(marketDataClient.getRatios(symbol)).thenReturn(
                new RatioSnapshot(symbol, new BigDecimal("28.5"), null, null, null,
                        null, null, null, null, null, null, null));
        when(marketDataClient.getAnnualRatios(symbol)).thenReturn(List.of(
                new RatioSnapshot(symbol, new BigDecimal("28.5"), null, new BigDecimal("1.8"),
                        new BigDecimal("0.20"), null, new BigDecimal("0.14"), null, null,
                        null, null, null, null, null, null),
                new RatioSnapshot(symbol, new BigDecimal("25.0"), null, new BigDecimal("1.6"),
                        new BigDecimal("0.18"), null, new BigDecimal("0.13"), null, null,
                        null, null, null, null, null, null)
        ));
        when(marketDataClient.getQuote(symbol)).thenReturn(
                new MarketPriceQuote(symbol, new BigDecimal("182.50"), "USD", null, null, 1_000_000L));
        when(marketDataClient.getDividendHistory(symbol)).thenReturn(List.of(
                new FmpDividendEntry("2026-06-30", new BigDecimal("0.82"), "2026-07-24")));
        when(marketDataClient.getInsiderTransactions(symbol)).thenReturn(List.of(
                new FmpInsiderTradingEntry(symbol, "2026-06-15", "Jane Insider",
                        "Chief Financial Officer", "S-Sale", 1200L, new BigDecimal("99.25"))));
    }

    private void stubValuation(String symbol, BigDecimal composite, BigDecimal mos, Recommendation rec) {
        ValuationResult result = new ValuationResult();
        result.setCompositeFairValue(composite);
        result.setMarginOfSafety(mos);
        result.setRecommendation(rec);
        when(valuationService.calculate(eq(symbol), any()))
                .thenReturn(new ValuationOutcome(result, Map.<String, BigDecimal>of()));
        ValueScore score = new ValueScore();
        score.setTotalScore(new BigDecimal("66.00"));
        when(valueScoreService.compute(symbol)).thenReturn(score);
    }
}
