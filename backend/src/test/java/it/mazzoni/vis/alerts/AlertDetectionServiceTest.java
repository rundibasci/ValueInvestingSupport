package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDetectionServiceTest {
    @Mock WatchlistItemRepository watchlistItems; @Mock HoldingRepository holdings; @Mock SecurityRepository securities;
    @Mock ValuationResultRepository valuations; @Mock ValueScoreRepository scores; @Mock PriceQuoteRepository quotes;
    @Mock DividendRecordRepository dividends; @Mock InsiderTradeRepository insiderTrades;
    @Mock FundamentalSnapshotRepository fundamentals; @Mock RebalanceProposalRepository rebalanceProposals;
    @Mock AlertRepository alerts;
    @Mock AlertDeliveryService alertDeliveryService;
    private AlertDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AlertDetectionService(watchlistItems, holdings, securities, valuations, scores, quotes,
                dividends, insiderTrades, fundamentals, rebalanceProposals, alerts, alertDeliveryService);
        when(holdings.findAll()).thenReturn(List.of());
        when(dividends.findBySecurityOrderByExDividendDateDesc(any())).thenReturn(List.of());
        when(insiderTrades.findBySecurityOrderByTradeDateDesc(any())).thenReturn(List.of());
        when(fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(any(), any())).thenReturn(List.of());
    }

    @Test
    void createsPriceAlertAtExactlyFivePercentMovement() {
        User user = new User();
        Watchlist watchlist = new Watchlist(); watchlist.setUser(user);
        WatchlistItem item = new WatchlistItem(); item.setWatchlist(watchlist); item.setSymbol("AAPL");
        Security security = new Security(); security.setSymbol("AAPL");
        PriceQuote current = quote("105");
        PriceQuote previous = quote("100");
        when(watchlistItems.findAll()).thenReturn(List.of(item));
        when(securities.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        when(quotes.findTop2BySecurityOrderByQuoteDateDesc(security)).thenReturn(List.of(current, previous));
        when(alerts.existsByUserAndSymbolAndAlertTypeAndTriggeredAtBetween(any(), anyString(), any(), any(), any())).thenReturn(false);

        assertThat(service.execute()).isEqualTo(1);
        ArgumentCaptor<Alert> captured = ArgumentCaptor.forClass(Alert.class);
        verify(alerts).save(captured.capture());
        assertThat(captured.getValue().getAlertType()).isEqualTo(AlertType.PRICE_TARGET_HIT);
        assertThat(captured.getValue().getThreshold()).isEqualByComparingTo("5");
        assertThat(captured.getValue().getStatus()).isEqualTo(AlertStatus.ACTIVE);
    }

    @Test
    void doesNotCreatePriceAlertBelowFivePercentMovement() {
        User user = new User();
        Watchlist watchlist = new Watchlist(); watchlist.setUser(user);
        WatchlistItem item = new WatchlistItem(); item.setWatchlist(watchlist); item.setSymbol("AAPL");
        Security security = new Security(); security.setSymbol("AAPL");
        when(watchlistItems.findAll()).thenReturn(List.of(item));
        when(securities.findBySymbol("AAPL")).thenReturn(Optional.of(security));
        when(quotes.findTop2BySecurityOrderByQuoteDateDesc(security)).thenReturn(List.of(quote("104.99"), quote("100")));

        assertThat(service.execute()).isZero();
        verify(alerts, never()).save(any());
    }

    private PriceQuote quote(String close) {
        PriceQuote quote = new PriceQuote(); quote.setClose(new BigDecimal(close)); return quote;
    }
}
