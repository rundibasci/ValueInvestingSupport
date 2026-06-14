package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SecurityRepository extends JpaRepository<Security, UUID> {
    Optional<Security> findBySymbol(String symbol);
    boolean existsBySymbol(String symbol);
}
