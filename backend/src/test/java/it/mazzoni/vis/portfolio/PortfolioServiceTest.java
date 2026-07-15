package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.HoldingRepository;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

    @Mock PortfolioRepository portfolios;
    @Mock HoldingRepository holdings;
    @Mock UserRepository users;
    @Mock SecurityRepository securities;
    @Mock PriceQuoteRepository prices;
    @Mock ValuationResultRepository valuations;
    @Mock Authentication authentication;

    @Test
    void deletePortfolio_resolvesOwnerBeforeDeleting() {
        UUID id = UUID.randomUUID();
        User user = new User();
        Portfolio portfolio = new Portfolio();
        portfolio.setUser(user);
        when(authentication.getName()).thenReturn("owner@example.com");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(portfolios.findByIdAndUser(id, user)).thenReturn(Optional.of(portfolio));

        service().deletePortfolio(authentication, id);

        verify(portfolios).delete(portfolio);
    }

    @Test
    void deletePortfolio_unknownOrForeignPortfolioDoesNotDelete() {
        UUID id = UUID.randomUUID();
        User user = new User();
        when(authentication.getName()).thenReturn("owner@example.com");
        when(users.findByEmail("owner@example.com")).thenReturn(Optional.of(user));
        when(portfolios.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().deletePortfolio(authentication, id))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verify(portfolios, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private PortfolioService service() {
        return new PortfolioService(portfolios, holdings, users, securities, prices, valuations);
    }
}
