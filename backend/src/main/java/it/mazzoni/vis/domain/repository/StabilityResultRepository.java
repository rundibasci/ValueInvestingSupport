package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.StabilityResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface StabilityResultRepository extends JpaRepository<StabilityResult, UUID> {
    List<StabilityResult> findBySecurityAndResultDateOrderByCriterionCodeAsc(Security security, LocalDate resultDate);
    List<StabilityResult> findBySecurityOrderByResultDateDescCriterionCodeAsc(Security security);
    long deleteBySecurity(Security security);
}
