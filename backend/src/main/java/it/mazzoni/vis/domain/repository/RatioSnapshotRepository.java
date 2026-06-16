package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface RatioSnapshotRepository extends JpaRepository<RatioSnapshot, UUID> {
    List<RatioSnapshot> findBySecurityAndPeriodOrderByReportDateDesc(Security security, Period period);
    List<RatioSnapshot> findBySecurity(Security security);
    boolean existsBySecurityAndPeriodAndReportDate(Security security, Period period, LocalDate reportDate);
}
