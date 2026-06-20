package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityRepository extends JpaRepository<Security, UUID>, JpaSpecificationExecutor<Security> {
    Optional<Security> findBySymbol(String symbol);
    boolean existsBySymbol(String symbol);

    @Query("SELECT DISTINCT s.sector FROM Security s WHERE s.sector IS NOT NULL ORDER BY s.sector")
    List<String> findDistinctSectors();

    @Query("SELECT DISTINCT s.exchange FROM Security s WHERE s.exchange IS NOT NULL ORDER BY s.exchange")
    List<String> findDistinctExchanges();
}
