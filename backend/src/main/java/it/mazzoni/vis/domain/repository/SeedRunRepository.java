package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.SeedRun;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeedRunRepository extends JpaRepository<SeedRun, UUID> {
    @Modifying
    @Query("update SeedRun r set r.status = 'RUNNING', r.startedAt = :now, r.updatedAt = :now " +
            "where r.id = :id and r.status = 'QUEUED'")
    int claimQueued(@Param("id") UUID id, @Param("now") LocalDateTime now);

    Optional<SeedRun> findByIdAndUser(UUID id, User user);
    Optional<SeedRun> findFirstByUserAndScopeAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(
            User user, String scope, String fingerprint, Collection<String> statuses);
    List<SeedRun> findByStatusIn(Collection<String> statuses);
    List<SeedRun> findByStatusNotInAndCompletedAtBefore(Collection<String> statuses, LocalDateTime cutoff);
}
