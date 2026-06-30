package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.AltmanResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AltmanResultRepository extends JpaRepository<AltmanResult, UUID> {
    Optional<AltmanResult> findTopBySecurityOrderByResultDateDesc(Security security);
    Optional<AltmanResult> findTopBySecuritySymbolOrderByResultDateDesc(String symbol);
}
