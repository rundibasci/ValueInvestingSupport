package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.JobRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface JobRunLogRepository extends JpaRepository<JobRunLog, UUID> {
    Optional<JobRunLog> findTop1ByJobNameOrderByStartedAtDesc(String jobName);
}
