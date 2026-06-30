package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.CyclicalityResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CyclicalityResultRepository extends JpaRepository<CyclicalityResult, UUID> {
    Optional<CyclicalityResult> findTopBySecurityOrderByResultDateDesc(Security security);
}
