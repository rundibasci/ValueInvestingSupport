package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.entity.UserCompetencePreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCompetencePreferencesRepository extends JpaRepository<UserCompetencePreferences, UUID> {
    Optional<UserCompetencePreferences> findByUser(User user);
}
