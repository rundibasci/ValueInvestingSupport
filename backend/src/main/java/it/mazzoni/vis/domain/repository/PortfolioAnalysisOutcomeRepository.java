package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface PortfolioAnalysisOutcomeRepository extends JpaRepository<PortfolioAnalysisOutcome, UUID> {
    Page<PortfolioAnalysisOutcome> findByAnalysisRunOrderByPosition(PortfolioAnalysisRun run, Pageable pageable);
    List<PortfolioAnalysisOutcome> findByAnalysisRunOrderByPosition(PortfolioAnalysisRun run);
    Optional<PortfolioAnalysisOutcome> findByAnalysisRunAndSymbol(PortfolioAnalysisRun run, String symbol);
}
