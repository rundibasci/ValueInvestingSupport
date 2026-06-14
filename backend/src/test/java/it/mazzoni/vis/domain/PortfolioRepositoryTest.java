package it.mazzoni.vis.domain;

import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserRole;
import it.mazzoni.vis.domain.repository.PortfolioRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PortfolioRepositoryTest {

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private UserRepository userRepository;

    private User owner;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setEmail("investor@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.INVESTOR);
        owner = userRepository.save(user);
    }

    @Test
    void savesPortfolioLinkedToUser() {
        Portfolio p = new Portfolio();
        p.setUser(owner);
        p.setName("Value Portfolio");
        portfolioRepository.save(p);

        List<Portfolio> found = portfolioRepository.findByUser(owner);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo("Value Portfolio");
        assertThat(found.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    void savesPortfolioWithHoldings() {
        Portfolio p = new Portfolio();
        p.setUser(owner);
        p.setName("Tech Portfolio");

        Holding h = new Holding();
        h.setPortfolio(p);
        h.setSymbol("AAPL");
        h.setQuantity(new BigDecimal("10.5"));
        h.setAverageCostBasis(new BigDecimal("150.00"));
        p.getHoldings().add(h);

        portfolioRepository.save(p);

        Portfolio saved = portfolioRepository.findByUser(owner).get(0);
        assertThat(saved.getHoldings()).hasSize(1);
        assertThat(saved.getHoldings().get(0).getSymbol()).isEqualTo("AAPL");
    }

    @Test
    void findByUserReturnsOnlyOwnedPortfolios() {
        User other = new User();
        other.setEmail("other@example.com");
        other.setPasswordHash("hash");
        other.setRole(UserRole.INVESTOR);
        userRepository.save(other);

        Portfolio ownerPortfolio = new Portfolio();
        ownerPortfolio.setUser(owner);
        ownerPortfolio.setName("Mine");
        portfolioRepository.save(ownerPortfolio);

        Portfolio otherPortfolio = new Portfolio();
        otherPortfolio.setUser(other);
        otherPortfolio.setName("Theirs");
        portfolioRepository.save(otherPortfolio);

        assertThat(portfolioRepository.findByUser(owner)).hasSize(1);
        assertThat(portfolioRepository.findByUser(other)).hasSize(1);
    }
}
