package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.PortfolioCashBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioCashBalanceRepository extends JpaRepository<PortfolioCashBalance, UUID> {
    List<PortfolioCashBalance> findByPortfolio(Portfolio portfolio);
    Optional<PortfolioCashBalance> findByPortfolioAndCurrency(Portfolio portfolio, String currency);
    void deleteByPortfolio(Portfolio portfolio);
}
