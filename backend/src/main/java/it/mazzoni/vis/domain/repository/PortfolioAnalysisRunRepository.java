package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;

public interface PortfolioAnalysisRunRepository extends JpaRepository<PortfolioAnalysisRun, UUID> {
    Optional<PortfolioAnalysisRun> findByIdAndUserAndPortfolio(UUID id, User user, Portfolio portfolio);
    Optional<PortfolioAnalysisRun> findFirstByUserAndPortfolioOrderByCreatedAtDesc(User user, Portfolio portfolio);
    Optional<PortfolioAnalysisRun> findFirstByUserAndPortfolioAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(User user, Portfolio portfolio, String fingerprint, Collection<String> statuses);
    List<PortfolioAnalysisRun> findByStatusIn(Collection<String> statuses);
    @Modifying @Query("update PortfolioAnalysisRun r set r.status='RUNNING', r.phase='SEEDING', r.startedAt=:now, r.updatedAt=:now where r.id=:id and r.status='QUEUED'")
    int claimQueued(@Param("id") UUID id, @Param("now") LocalDateTime now);
}
