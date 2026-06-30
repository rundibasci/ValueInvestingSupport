package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.CapitalAllocationResult;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CapitalAllocationResultRepository extends JpaRepository<CapitalAllocationResult, UUID> {
    Optional<CapitalAllocationResult> findTopBySecurityOrderByResultDateDesc(Security security);
    Optional<CapitalAllocationResult> findTopBySecuritySymbolOrderByResultDateDesc(String symbol);
    long deleteBySecurity(Security security);
}
