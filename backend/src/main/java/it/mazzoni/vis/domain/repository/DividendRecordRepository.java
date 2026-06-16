package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DividendRecordRepository extends JpaRepository<DividendRecord, UUID> {
    List<DividendRecord> findBySecurityOrderByExDividendDateDesc(Security security);
    Optional<DividendRecord> findBySecurityAndExDividendDate(Security security, LocalDate exDividendDate);
}
