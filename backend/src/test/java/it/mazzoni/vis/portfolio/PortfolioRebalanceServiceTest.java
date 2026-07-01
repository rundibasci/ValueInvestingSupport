package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.portfolio.dto.RebalanceProposalResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioRebalanceServiceTest {
    @Mock PortfolioRepository portfolios; @Mock HoldingRepository holdings; @Mock UserRepository users;
    @Mock SecurityRepository securities; @Mock PriceQuoteRepository quotes; @Mock RebalanceProposalRepository proposals;
    @Mock PortfolioSimulationService simulation;

    @Test
    void responseIncludesCostUrgencyAndHoldingPeriod() throws Exception {
        PortfolioRebalanceService service = new PortfolioRebalanceService(portfolios, holdings, users, securities,
                quotes, proposals, simulation);
        UUID portfolioId = UUID.randomUUID();
        UUID proposalId = UUID.randomUUID();
        User user = new User(); user.setEmail("investor@example.com");
        Portfolio portfolio = new Portfolio(); portfolio.setId(portfolioId); portfolio.setUser(user);
        Holding holding = new Holding(); holding.setPortfolio(portfolio); holding.setSymbol("AAA"); holding.setQuantity(BigDecimal.TEN);
        setAddedAt(holding, LocalDateTime.now().minusMonths(2));
        Security security = new Security(); security.setSymbol("AAA"); security.setCompanyName("AAA");
        PriceQuote quote = new PriceQuote(); quote.setClose(new BigDecimal("10"));
        RebalanceProposal proposal = new RebalanceProposal(); proposal.setPortfolio(portfolio); proposal.setStatus("PENDING"); proposal.setHoldingsFingerprint("x");
        RebalanceLine line = new RebalanceLine(); line.setProposal(proposal); line.setSymbol("AAA"); line.setCapturedPrice(new BigDecimal("10"));
        line.setCurrentQuantity(BigDecimal.TEN); line.setTargetQuantity(new BigDecimal("5"));
        proposal.getLines().add(line);

        when(users.findByEmail("investor@example.com")).thenReturn(Optional.of(user));
        when(portfolios.findByIdAndUser(portfolioId, user)).thenReturn(Optional.of(portfolio));
        when(proposals.findByIdAndPortfolio(proposalId, portfolio)).thenReturn(Optional.of(proposal));
        when(holdings.findByPortfolio(portfolio)).thenReturn(List.of(holding));
        when(holdings.findByPortfolioAndSymbol(portfolio, "AAA")).thenReturn(List.of(holding));
        when(securities.findBySymbol("AAA")).thenReturn(Optional.of(security));
        when(quotes.findTopBySecurityOrderByQuoteDateDesc(security)).thenReturn(Optional.of(quote));

        RebalanceProposalResponse response = service.get(
                new UsernamePasswordAuthenticationToken("investor@example.com", "n/a"), portfolioId, proposalId);

        assertEquals(new BigDecimal("0.05"), response.totalEstimatedTransactionCost());
        assertEquals("COULD", response.lines().getFirst().urgency());
        assertEquals("SHORT_TERM", response.lines().getFirst().holdingPeriod());
        assertTrue(response.disclaimer().contains("decision-support"));
    }

    private void setAddedAt(Holding holding, LocalDateTime addedAt) throws Exception {
        java.lang.reflect.Field field = Holding.class.getDeclaredField("addedAt");
        field.setAccessible(true);
        field.set(holding, addedAt);
    }
}
