package it.mazzoni.vis.admin;

import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.marketdata.MarketDataClient;
import it.mazzoni.vis.marketdata.fmp.dto.FmpStockListEntry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniverseSelectionServiceTest {

    private final MarketDataClient marketDataClient = mock(MarketDataClient.class);
    private final SeedService seedService = mock(SeedService.class);
    private final UniverseSelectionService service = new UniverseSelectionService(marketDataClient, seedService);

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
    void templates_includeRoadmapTemplates() {
        assertThat(service.templates())
                .extracting(UniverseTemplateResponse::id)
                .containsExactly("us-blue-chip", "dividend-aristocrats", "value-candidates", "defensive-quality");
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
}
