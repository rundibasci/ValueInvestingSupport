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
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.valuation.ValuationOutcome;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeedServiceTest {

    @Mock MarketDataClient marketDataClient;
    @Mock SecurityRepository securityRepository;
    @Mock FundamentalSnapshotRepository fundamentalSnapshotRepository;
    @Mock RatioSnapshotRepository ratioSnapshotRepository;
    @Mock PriceQuoteRepository priceQuoteRepository;
    @Mock ValueScoreRepository valueScoreRepository;
    @Mock ValuationService valuationService;
    @Mock SourceTracker sourceTracker;

    SeedService seedService;

    @BeforeEach
    void setUp() {
        ValuationDefaultsProperties defaults = new ValuationDefaultsProperties(
                new BigDecimal("0.09"), new BigDecimal("0.08"),
                new BigDecimal("0.04"), new BigDecimal("0.025"));
        seedService = new SeedService(marketDataClient, securityRepository,
                fundamentalSnapshotRepository, ratioSnapshotRepository,
                priceQuoteRepository, valueScoreRepository, valuationService, defaults, sourceTracker);

        // Shared lenient stubs — save always returns the argument, exists-checks default to false.
        Mockito.lenient().when(securityRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(fundamentalSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(ratioSnapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(priceQuoteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(fundamentalSnapshotRepository.existsBySecurityAndPeriodAndReportDate(
                any(), any(), any())).thenReturn(false);
        Mockito.lenient().when(ratioSnapshotRepository.existsBySecurityAndPeriodAndReportDate(
                any(), any(), any())).thenReturn(false);
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
        assertThat(results.get(0).error()).isNull();
        assertThat(results.get(1).symbol()).isEqualTo("KO");
        assertThat(results.get(1).marginOfSafety()).isEqualByComparingTo("18.40");
        assertThat(results.get(1).error()).isNull();
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
    }

    @Test
    void seedTickers_fundamentalsAlreadyTodaySkipsInsert() {
        stubFmpData("AAPL", "Apple Inc.");
        stubValuation("AAPL", new BigDecimal("200.00"), new BigDecimal("10.00"), Recommendation.QUALITY_VALUE);
        when(fundamentalSnapshotRepository.existsBySecurityAndPeriodAndReportDate(
                any(), eq(Period.ANNUAL), eq(LocalDate.now()))).thenReturn(true);

        seedService.seedTickers(List.of("AAPL"));

        verify(fundamentalSnapshotRepository, never()).save(any());
        verify(marketDataClient, never()).getFundamentals(any());
    }

    private void stubFmpData(String symbol, String companyName) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(companyName);

        when(marketDataClient.getProfile(symbol)).thenReturn(
                new CompanyProfile(symbol, companyName, "Technology", "Consumer Electronics",
                        "US", "USD", "NASDAQ", new BigDecimal("2000000000000")));
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
        when(marketDataClient.getRatios(symbol)).thenReturn(
                new RatioSnapshot(symbol, new BigDecimal("28.5"), null, null, null,
                        null, null, null, null, null, null, null));
        when(marketDataClient.getQuote(symbol)).thenReturn(
                new MarketPriceQuote(symbol, new BigDecimal("182.50"), "USD", null, null));
    }

    private void stubValuation(String symbol, BigDecimal composite, BigDecimal mos, Recommendation rec) {
        ValuationResult result = new ValuationResult();
        result.setCompositeFairValue(composite);
        result.setMarginOfSafety(mos);
        result.setRecommendation(rec);
        when(valuationService.calculate(eq(symbol), any()))
                .thenReturn(new ValuationOutcome(result, Map.of()));
    }
}
