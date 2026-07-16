package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

public interface PortfolioImportRepository extends JpaRepository<PortfolioImport, UUID> {
    Optional<PortfolioImport> findByIdAndUser(UUID id, User user);
    long deleteByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);
}
