package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Portfolio;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {
    List<Portfolio> findByUser(User user);
    List<Portfolio> findByUserOrderByCreatedAtDesc(User user);
    Optional<Portfolio> findByIdAndUser(UUID id, User user);
}
