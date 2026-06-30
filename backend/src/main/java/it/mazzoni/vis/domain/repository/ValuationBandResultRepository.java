package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationBandResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ValuationBandResultRepository extends JpaRepository<ValuationBandResult, UUID> {
    List<ValuationBandResult> findBySecurityAndResultDateOrderByMetricAsc(Security security, LocalDate resultDate);
    List<ValuationBandResult> findBySecurityOrderByResultDateDescMetricAsc(Security security);
    long deleteBySecurity(Security security);
}
