package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.SeedRun;
import it.mazzoni.vis.domain.entity.SeedRunOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeedRunOutcomeRepository extends JpaRepository<SeedRunOutcome, UUID> {
    Page<SeedRunOutcome> findBySeedRunOrderByPosition(SeedRun run, Pageable pageable);
    List<SeedRunOutcome> findBySeedRunOrderByPosition(SeedRun run);
}
