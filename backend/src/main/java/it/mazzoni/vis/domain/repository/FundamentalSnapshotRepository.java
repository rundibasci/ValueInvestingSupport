package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface FundamentalSnapshotRepository extends JpaRepository<FundamentalSnapshot, UUID> {
    List<FundamentalSnapshot> findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(Security security, Period period);
    List<FundamentalSnapshot> findBySecurity(Security security);
}
