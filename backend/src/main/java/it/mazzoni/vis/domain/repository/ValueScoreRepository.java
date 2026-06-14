package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValueScore;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ValueScoreRepository extends JpaRepository<ValueScore, UUID> {
    Optional<ValueScore> findTopBySecurityOrderByScoreDateDesc(Security security);
}
