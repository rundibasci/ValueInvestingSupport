package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.AdvisorAcknowledgement;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdvisorAcknowledgementRepository extends JpaRepository<AdvisorAcknowledgement, UUID> {
    Optional<AdvisorAcknowledgement> findByUser(User user);
}
