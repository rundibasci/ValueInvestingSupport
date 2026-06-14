package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DividendRecordRepository extends JpaRepository<DividendRecord, UUID> {
    List<DividendRecord> findBySecurityOrderByExDividendDateDesc(Security security);
}
