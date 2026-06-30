package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.MoatResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MoatResultRepository extends JpaRepository<MoatResult, UUID> {
    Optional<MoatResult> findTopBySecurityOrderByResultDateDesc(Security security);
    Optional<MoatResult> findTopBySecuritySymbolOrderByResultDateDesc(String symbol);
    long deleteBySecurity(Security security);
}
