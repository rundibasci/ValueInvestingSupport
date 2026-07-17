package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SecurityRepository extends JpaRepository<Security, UUID>, JpaSpecificationExecutor<Security> {
    Optional<Security> findBySymbol(String symbol);
    Optional<Security> findByIsin(String isin);
    List<Security> findByIsinIn(List<String> isins);
    boolean existsBySymbol(String symbol);

    List<Security> findTop10BySymbolContainingIgnoreCaseOrCompanyNameContainingIgnoreCase(String symbol, String companyName);

    List<Security> findByActiveTrueAndSectorAndSymbolNot(String sector, String symbol);

    @Query("SELECT DISTINCT s.sector FROM Security s WHERE s.active = true AND s.sector IS NOT NULL ORDER BY s.sector")
    List<String> findDistinctSectors();

    @Query("SELECT DISTINCT s.exchange FROM Security s WHERE s.active = true AND s.exchange IS NOT NULL ORDER BY s.exchange")
    List<String> findDistinctExchanges();

    @Modifying
    @Transactional
    @Query("UPDATE Security s SET s.active = false WHERE s.active = true")
    int deactivateAll();
}
