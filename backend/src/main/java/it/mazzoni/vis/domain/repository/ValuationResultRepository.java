package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValuationResultRepository extends JpaRepository<ValuationResult, UUID> {
    Optional<ValuationResult> findTopBySecurityOrderByValuationDateDesc(Security security);
    List<ValuationResult> findBySecurityOrderByValuationDateDesc(Security security);
    boolean existsBySecurityAndValuationDateAndSource(Security security, LocalDate valuationDate, String source);
}
