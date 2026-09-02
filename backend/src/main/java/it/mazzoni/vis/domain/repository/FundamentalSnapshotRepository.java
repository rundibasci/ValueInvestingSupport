package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FundamentalSnapshotRepository extends JpaRepository<FundamentalSnapshot, UUID> {
    List<FundamentalSnapshot> findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(Security security, Period period);
    Optional<FundamentalSnapshot> findTopBySecurityAndPeriodOrderByReportDateDesc(Security security, Period period);
    List<FundamentalSnapshot> findBySecurity(Security security);
    boolean existsBySecurityAndPeriodAndReportDate(Security security, Period period, LocalDate reportDate);
    // RM2 (specs/sector-aware-valuation-metrics.md §2, §7): pairs a RatioSnapshot row with the
    // FundamentalSnapshot row for the same security/period/reportDate — both ingestion paths
    // (SeedTickerService, BulkFundamentalsSyncJob/BulkRatiosSyncJob) persist matching report dates
    // per period, so this lookup is expected to find a row whenever both syncs have run.
    Optional<FundamentalSnapshot> findBySecurityAndPeriodAndReportDate(Security security, Period period, LocalDate reportDate);
    long deleteBySecurityAndPeriod(Security security, Period period);
    long deleteBySecurityAndPeriodAndFiscalYear(Security security, Period period, Integer fiscalYear);
}
