package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    List<Holding> findByPortfolio(Portfolio portfolio);
    List<Holding> findByPortfolioAndSymbol(Portfolio portfolio, String symbol);
}
