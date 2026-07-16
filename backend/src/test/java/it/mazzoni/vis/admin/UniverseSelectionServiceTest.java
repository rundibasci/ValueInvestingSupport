package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.MarketDataException;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniverseSelectionServiceTest {

    private final MarketDataClient marketDataClient = mock(MarketDataClient.class);
    private final SecurityRepository securityRepository = mock(SecurityRepository.class);
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository = mock(FundamentalSnapshotRepository.class);
    private final PriceQuoteRepository priceQuoteRepository = mock(PriceQuoteRepository.class);
    private final SeedService seedService = mock(SeedService.class);
    private final UniverseSelectionService service = new UniverseSelectionService(
            marketDataClient, securityRepository, fundamentalSnapshotRepository, priceQuoteRepository, seedService);

    @Test
    void preview_filtersSortsAndCapsSymbolsBeforeSeeding() {
        when(marketDataClient.listSymbols("NYSE")).thenReturn(List.of(
                stock("AAPL", "Apple Inc.", "US", "Technology", "NASDAQ", new BigDecimal("3000000000000"), 70000000L),
                stock("KO", "Coca-Cola", "US", "Consumer Staples", "NYSE", new BigDecimal("260000000000"), 15000000L),
                stock("SAP", "SAP SE", "DE", "Technology", "NYSE", new BigDecimal("210000000000"), 3000000L),
                stock("LOWVOL", "Low Volume", "US", "Technology", "NYSE", new BigDecimal("50000000000"), 5000L),
                stock("SMALL", "Small Co", "US", "Technology", "NYSE", new BigDecimal("1000000000"), 9000000L)
        ));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NYSE"),
                List.of("US"),
                List.of("Technology"),
                false,
                new BigDecimal("10000000000"),
                null,
                1000000L,
                1,
                UniverseSortBy.MARKET_CAP));

        assertThat(response.totalMatches()).isEqualTo(1);
        assertThat(response.returnedCount()).isEqualTo(1);
        assertThat(response.capped()).isFalse();
        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol).containsExactly("AAPL");
    }

    @Test
    void preview_reportsCapWarningWhenMatchesExceedMaxSymbols() {
        when(marketDataClient.listSymbols("NASDAQ")).thenReturn(List.of(
                stock("MSFT", "Microsoft", "US", "Technology", "NASDAQ", new BigDecimal("3100000000000"), 20000000L),
                stock("NVDA", "NVIDIA", "US", "Technology", "NASDAQ", new BigDecimal("2800000000000"), 50000000L),
                stock("ADP", "ADP", "US", "Industrials", "NASDAQ", new BigDecimal("100000000000"), 2000000L)
        ));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NASDAQ"), List.of("US"), List.of(), false,
                null, null, null, 2, UniverseSortBy.ALPHABETICAL));

        assertThat(response.totalMatches()).isEqualTo(3);
        assertThat(response.returnedCount()).isEqualTo(2);
        assertThat(response.capped()).isTrue();
        assertThat(response.warning()).contains("Results capped at 2 symbols");
        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol).containsExactly("ADP", "MSFT");
    }

    @Test
    void preview_supportsFrontendMarketCapAscendingSortValue() {
        when(marketDataClient.listSymbols("NYSE")).thenReturn(List.of(
                stock("BIG", "Big Co", "US", "Industrials", "NYSE", new BigDecimal("300000000000"), 1000000L),
                stock("SMALL", "Small Co", "US", "Industrials", "NYSE", new BigDecimal("1000000000"), 1000000L),
                stock("MID", "Mid Co", "US", "Industrials", "NYSE", new BigDecimal("10000000000"), 1000000L)
        ));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of(), false,
                null, null, null, 10, UniverseSortBy.MARKET_CAP_ASC));

        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol)
                .containsExactly("SMALL", "MID", "BIG");
    }

    @Test
    void preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable() {
        when(marketDataClient.listSymbols("NYSE")).thenThrow(new MarketDataException(
                MarketDataException.ErrorCode.SERVICE_UNAVAILABLE, "NYSE"));
        Security ko = security("KO", "Coca-Cola", "US", "Consumer Staples", "NYSE", new BigDecimal("260000000000"));
        when(securityRepository.findAll()).thenReturn(List.of(
                ko,
                security("AAPL", "Apple Inc.", "US", "Technology", "NASDAQ", new BigDecimal("3000000000000"))
        ));
        PriceQuote koQuote = new PriceQuote();
        koQuote.setClose(new BigDecimal("60"));
        koQuote.setVolume(15_000_000L);
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(ko)).thenReturn(Optional.of(koQuote));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of(), false,
                null, null, 250000L, 10, UniverseSortBy.SYMBOL_ASC));

        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol).containsExactly("KO");
    }

    @Test
    void preview_fallsBackToSeededSecuritiesWhenFmpStockListIsEmpty() {
        when(marketDataClient.listSymbols("NASDAQ")).thenReturn(List.of());
        when(securityRepository.findAll()).thenReturn(List.of(
                security("MSFT", "Microsoft", "US", "Technology", "NASDAQ", new BigDecimal("3000000000000")),
                security("KO", "Coca-Cola", "US", "Consumer Defensive", "NYSE", new BigDecimal("260000000000"))
        ));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NASDAQ"), List.of("US"), List.of("Technology"), false,
                null, null, null, 10, UniverseSortBy.SYMBOL_ASC));

        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol).containsExactly("MSFT");
    }

    @Test
    void preview_derivesFallbackMarketCapAndRequiresKnownVolumeForNumericFilters() {
        when(marketDataClient.listSymbols("NASDAQ")).thenReturn(List.of());
        Security msft = security("MSFT", "Microsoft", "US", "Technology", "NASDAQ", null);
        Security unknownVolume = security("NOVOL", "No Volume", "US", "Technology", "NASDAQ", null);
        when(securityRepository.findAll()).thenReturn(List.of(msft, unknownVolume));

        FundamentalSnapshot fundamentals = new FundamentalSnapshot();
        fundamentals.setSharesOutstanding(100L);
        when(fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(msft, Period.ANNUAL))
                .thenReturn(Optional.of(fundamentals));
        PriceQuote quote = new PriceQuote();
        quote.setClose(new BigDecimal("20"));
        quote.setVolume(500_000L);
        when(priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(msft)).thenReturn(Optional.of(quote));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NASDAQ"), List.of("US"), List.of(), false,
                new BigDecimal("1500"), new BigDecimal("2500"), 250_000L, 10,
                UniverseSortBy.MARKET_CAP_ASC));

        assertThat(response.symbols()).extracting(UniversePreviewRow::symbol).containsExactly("MSFT");
        assertThat(response.symbols().getFirst().marketCap()).isEqualByComparingTo("2000");
        assertThat(response.symbols().getFirst().volume()).isEqualTo(500_000L);
    }

    @Test
    void templates_includeRoadmapTemplates() {
        assertThat(service.templates())
                .extracting(UniverseTemplateResponse::id)
                .containsExactly("us-blue-chip", "dividend-aristocrats", "value-candidates", "defensive-quality");
    }

    @Test
    void preview_doesNotAssignUnknownExchangeToEveryRequestedExchange() {
        when(marketDataClient.listSymbols("NYSE")).thenReturn(List.of());
        when(securityRepository.findAll()).thenReturn(List.of(
                security("UNKNOWN", "Unknown", "US", "Technology", null, new BigDecimal("1000000000"))));

        UniversePreviewResponse response = service.preview(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of(), false,
                null, null, null, 10, UniverseSortBy.SYMBOL_ASC));

        assertThat(response.symbols()).isEmpty();
    }

    @Test
    void preview_rejectsInvalidNumericCriteria() {
        assertThatThrownBy(() -> service.preview(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of(), false,
                new BigDecimal("100"), new BigDecimal("10"), null, 10,
                UniverseSortBy.SYMBOL_ASC)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of(), false,
                null, null, -1L, 0, UniverseSortBy.SYMBOL_ASC)))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void seed_usesPreviewSymbolsInReturnedOrder() {
        when(marketDataClient.listSymbols("NYSE")).thenReturn(List.of(
                stock("KO", "Coca-Cola", "US", "Consumer Staples", "NYSE", new BigDecimal("260000000000"), 15000000L)));
        when(seedService.seedTickers(List.of("KO"))).thenReturn(List.of(
                SeedResult.success("KO", "Coca-Cola", "Consumer Staples", "NYSE", "US", null,
                        new BigDecimal("60"), new BigDecimal("70"), new BigDecimal("16.7"),
                        new BigDecimal("78"), Recommendation.QUALITY_VALUE, "FMP", LocalDate.of(2026, 7, 1))));

        UniverseSeedCriteriaResponse response = service.seed(new UniverseSelectionRequest(
                List.of("NYSE"), List.of("US"), List.of("Consumer Staples"), false,
                null, null, null, 10, UniverseSortBy.ALPHABETICAL));

        assertThat(response.preview().symbols()).extracting(UniversePreviewRow::symbol).containsExactly("KO");
        assertThat(response.results()).extracting(SeedResult::symbol).containsExactly("KO");
        verify(seedService).seedTickers(List.of("KO"));
    }

    private static FmpStockListEntry stock(String symbol,
                                           String name,
                                           String country,
                                           String sector,
                                           String exchange,
                                           BigDecimal marketCap,
                                           Long volume) {
        return new FmpStockListEntry(symbol, name, country, sector, exchange, exchange, "stock",
                null, marketCap, volume);
    }

    private static Security security(String symbol,
                                     String name,
                                     String country,
                                     String sector,
                                     String exchange,
                                     BigDecimal marketCap) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(name);
        security.setCountry(country);
        security.setSector(sector);
        security.setExchange(exchange);
        security.setMarketCap(marketCap);
        return security;
    }
}
