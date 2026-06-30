package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PiotroskiResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PiotroskiResultRepository extends JpaRepository<PiotroskiResult, UUID> {
    Optional<PiotroskiResult> findTopBySecurityOrderByResultDateDesc(Security security);
}
