package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.CompositeWeightPreference;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompositeWeightPreferenceRepository extends JpaRepository<CompositeWeightPreference, UUID> {
    Optional<CompositeWeightPreference> findByUser(User user);
}
