package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ValuationResultRepository extends JpaRepository<ValuationResult, UUID> {
    Optional<ValuationResult> findTopBySecurityOrderByValuationDateDescIdDesc(Security security);

    default Optional<ValuationResult> findTopBySecurityOrderByValuationDateDesc(Security security) {
        return findTopBySecurityOrderByValuationDateDescIdDesc(security);
    }

    List<ValuationResult> findBySecurityOrderByValuationDateDesc(Security security);
    boolean existsBySecurityAndValuationDateAndSource(Security security, LocalDate valuationDate, String source);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ValuationResult v where v.security = :security and v.valuationDate = :valuationDate")
    void deleteBySecurityAndValuationDate(@Param("security") Security security,
                                          @Param("valuationDate") LocalDate valuationDate);
}
