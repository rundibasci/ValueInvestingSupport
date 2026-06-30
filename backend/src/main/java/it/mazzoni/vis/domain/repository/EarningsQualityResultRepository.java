package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.EarningsQualityResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EarningsQualityResultRepository extends JpaRepository<EarningsQualityResult, UUID> {
    Optional<EarningsQualityResult> findTopBySecurityOrderByResultDateDesc(Security security);
}
