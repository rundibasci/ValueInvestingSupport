package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.entity.WatchlistItem;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.dto.PortfolioSimulationResponse;
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

        PortfolioSimulationResponse response = service.simulate(new UsernamePasswordAuthenticationToken("investor@example.com", "n/a"),
                UUID.randomUUID(), new SimulationRequest(new BigDecimal("1000"), null, null, null, null, null));

        assertFalse(response.proposals().isEmpty());
        assertTrue(response.proposals().stream().allMatch(p -> p.proposedShares() > 0));
        assertTrue(response.proposals().stream().allMatch(p -> p.actualWeightPercent().compareTo(new BigDecimal("25.00")) <= 0));
        assertTrue(response.sectorWeights().stream().allMatch(w -> w.weightPercent().compareTo(new BigDecimal("40.00")) <= 0));
        assertTrue(response.countryWeights().stream().allMatch(w -> w.weightPercent().compareTo(new BigDecimal("50.00")) <= 0));
        assertEquals("MISS", response.excludedSymbols().getFirst().symbol());
        assertTrue(response.disclaimer().contains("MiFID II"));
        verify(portfolioService).getPortfolioDetail(any(), any());
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
        when(ratios.findTopBySecurityOrderByReportDateDesc(security)).thenReturn(Optional.empty());
    }
}
