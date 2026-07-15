package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
import it.mazzoni.vis.portfolio.dto.HoldingDetailItem;
import it.mazzoni.vis.portfolio.dto.PortfolioDetailResponse;
import it.mazzoni.vis.portfolio.dto.PortfolioPreconditionsResponse;
import it.mazzoni.vis.portfolio.dto.SimulationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PortfolioSimulationServiceTest {
    @Mock PortfolioService portfolioService; @Mock UserRepository users; @Mock WatchlistItemRepository watchlistItems;
    @Mock SecurityRepository securities; @Mock PriceQuoteRepository quotes; @Mock ValueScoreRepository scores;
    @Mock ValuationResultRepository valuations; @Mock RatioSnapshotRepository ratios;

    @Test
    void allocatesWholeSharesWithinDefaultCapsAndExcludesMissingData() {
        PortfolioSimulationService service = new PortfolioSimulationService(portfolioService, users, watchlistItems,
                securities, quotes, scores, valuations, ratios);
        User user = new User(); user.setEmail("investor@example.com");
        when(users.findByEmail("investor@example.com")).thenReturn(Optional.of(user));
        when(watchlistItems.findByWatchlist_UserOrderByAddedAtDesc(user)).thenReturn(List.of(item("AAA"), item("BBB"), item("MISS")));
        stub("AAA", "Technology", "US", "80", "80", "20");
        stub("BBB", "Healthcare", "UK", "50", "20", "10");
        when(securities.findBySymbol("MISS")).thenReturn(Optional.empty());

        UUID portfolioId = UUID.randomUUID();
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId))).thenReturn(detail(portfolioId, List.of()));
        PortfolioSimulationResponse response = service.simulate(new UsernamePasswordAuthenticationToken("investor@example.com", "n/a"),
                portfolioId, new SimulationRequest(new BigDecimal("1000"), null, null, null, null, null));

        assertFalse(response.proposals().isEmpty());
        assertTrue(response.proposals().stream().allMatch(p -> p.proposedShares() > 0));
        assertTrue(response.proposals().stream().allMatch(p -> p.actualWeightPercent().compareTo(new BigDecimal("25.00")) <= 0));
        assertTrue(response.sectorWeights().stream().allMatch(w -> w.weightPercent().compareTo(new BigDecimal("40.00")) <= 0));
        assertTrue(response.countryWeights().stream().allMatch(w -> w.weightPercent().compareTo(new BigDecimal("50.00")) <= 0));
        assertEquals("MISS", response.excludedSymbols().getFirst().symbol());
        assertTrue(response.disclaimer().contains("MiFID II"));
        verify(portfolioService).getPortfolioDetail(any(), any());
    }

    @Test
    void reportsEmptyWatchlistAndEmptyPortfolioWithoutBlockingInitialRebalanceForHoldings() {
        PortfolioSimulationService service = service();
        User user = user();
        UUID portfolioId = UUID.randomUUID();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(watchlistItems.findByWatchlist_UserOrderByAddedAtDesc(user)).thenReturn(List.of());
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId))).thenReturn(detail(portfolioId, List.of()));

        PortfolioPreconditionsResponse response = service.preconditions(auth(user), portfolioId, request());

        assertFalse(response.simulationAvailable());
        assertFalse(response.rebalanceAvailable());
        assertEquals(List.of("NO_WATCHLIST_ITEMS", "EMPTY_PORTFOLIO"),
                response.diagnostics().stream().map(diagnostic -> diagnostic.code()).toList());
        assertFalse(response.diagnostics().get(1).blocksRebalance());
    }

    @Test
    void distinguishesConstraintsThatExcludeEveryDataReadyCandidate() {
        PortfolioSimulationService service = service();
        User user = user();
        UUID portfolioId = UUID.randomUUID();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(watchlistItems.findByWatchlist_UserOrderByAddedAtDesc(user)).thenReturn(List.of(item("AAA")));
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId))).thenReturn(detail(portfolioId, List.of()));
        stub("AAA", "Technology", "US", "80", "80", "10");

        PortfolioPreconditionsResponse response = service.preconditions(auth(user), portfolioId,
                new SimulationRequest(new BigDecimal("1000"), null, null, null, new BigDecimal("20"), null));

        assertFalse(response.simulationAvailable());
        assertEquals("CONSTRAINTS_EXCLUDE_ALL", response.diagnostics().getFirst().code());
        assertEquals(1L, response.exclusionCounts().get("BELOW_MINIMUM_MARGIN_OF_SAFETY"));
    }

    @Test
    void blocksRebalanceWhenAnExistingHoldingHasNoPrice() {
        PortfolioSimulationService service = service();
        User user = user();
        UUID portfolioId = UUID.randomUUID();
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(watchlistItems.findByWatchlist_UserOrderByAddedAtDesc(user)).thenReturn(List.of(item("AAA")));
        HoldingDetailItem holding = new HoldingDetailItem(UUID.randomUUID(), "MISS", null, BigDecimal.ONE,
                null, "USD", null, null, null, null, null, null, "UNAVAILABLE", LocalDateTime.now());
        when(portfolioService.getPortfolioDetail(any(), eq(portfolioId))).thenReturn(detail(portfolioId, List.of(holding)));
        stub("AAA", "Technology", "US", "80", "80", "20");

        PortfolioPreconditionsResponse response = service.preconditions(auth(user), portfolioId, request());

        assertTrue(response.simulationAvailable());
        assertFalse(response.rebalanceAvailable());
        assertEquals("UNPRICED_PORTFOLIO", response.diagnostics().getFirst().code());
    }

    private PortfolioSimulationService service() {
        return new PortfolioSimulationService(portfolioService, users, watchlistItems, securities, quotes, scores,
                valuations, ratios);
    }

    private User user() { User user = new User(); user.setEmail("investor@example.com"); return user; }
    private UsernamePasswordAuthenticationToken auth(User user) {
        return new UsernamePasswordAuthenticationToken(user.getEmail(), "n/a");
    }
    private SimulationRequest request() {
        return new SimulationRequest(new BigDecimal("1000"), null, null, null, null, null);
    }
    private PortfolioDetailResponse detail(UUID id, List<HoldingDetailItem> holdings) {
        return new PortfolioDetailResponse(id, "Portfolio", null, null, null, holdings, List.of(),
                LocalDateTime.now(), LocalDateTime.now());
    }

    private WatchlistItem item(String symbol) { WatchlistItem item = new WatchlistItem(); item.setSymbol(symbol); return item; }
    private void stub(String symbol, String sector, String country, String price, String score, String mos) {
        Security security = new Security(); security.setSymbol(symbol); security.setCompanyName(symbol); security.setSector(sector); security.setCountry(country);
        PriceQuote quote = new PriceQuote(); quote.setClose(new BigDecimal(price));
        ValueScore valueScore = new ValueScore(); valueScore.setTotalScore(new BigDecimal(score));
        ValuationResult valuation = new ValuationResult(); valuation.setMarginOfSafety(new BigDecimal(mos));
        when(securities.findBySymbol(symbol)).thenReturn(Optional.of(security));
        when(quotes.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));
        when(scores.findTopBySecurityOrderByScoreDateDesc(security)).thenReturn(Optional.of(valueScore));
        when(valuations.findTopBySecurityOrderByValuationDateDesc(security)).thenReturn(Optional.of(valuation));
        lenient().when(ratios.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.empty());
    }
}
