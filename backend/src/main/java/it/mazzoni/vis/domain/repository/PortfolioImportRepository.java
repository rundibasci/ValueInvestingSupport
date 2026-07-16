package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.PortfolioImport;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import it.mazzoni.vis.domain.entity.Portfolio;

public interface PortfolioImportRepository extends JpaRepository<PortfolioImport, UUID> {
    Optional<PortfolioImport> findByIdAndUser(UUID id, User user);
    long deleteByStatusAndExpiresAtBefore(String status, LocalDateTime cutoff);
    Page<PortfolioImport> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<PortfolioImport> findByUserAndPortfolioOrderByCreatedAtDesc(User user, Portfolio portfolio, Pageable pageable);
    Page<PortfolioImport> findByUserAndStatusOrderByCreatedAtDesc(User user, String status, Pageable pageable);
    Page<PortfolioImport> findByUserAndPortfolioAndStatusOrderByCreatedAtDesc(User user, Portfolio portfolio,
                                                                              String status, Pageable pageable);
}
