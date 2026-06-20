package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Holding;
import it.mazzoni.vis.domain.entity.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HoldingRepository extends JpaRepository<Holding, UUID> {
    List<Holding> findByPortfolio(Portfolio portfolio);
    List<Holding> findByPortfolioAndSymbol(Portfolio portfolio, String symbol);
    List<Holding> findByPortfolioOrderByAddedAtDesc(Portfolio portfolio);
    Optional<Holding> findByIdAndPortfolio(UUID id, Portfolio portfolio);

    @Query("SELECT DISTINCT h.symbol FROM Holding h")
    List<String> findAllDistinctSymbols();
}
