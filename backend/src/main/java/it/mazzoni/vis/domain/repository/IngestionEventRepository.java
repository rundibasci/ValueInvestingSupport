package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.IngestionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface IngestionEventRepository extends JpaRepository<IngestionEvent, UUID>, JpaSpecificationExecutor<IngestionEvent> {
    Optional<IngestionEvent> findTop1ByJobRunIdOrderByOccurredAtDesc(UUID jobRunId);
}
