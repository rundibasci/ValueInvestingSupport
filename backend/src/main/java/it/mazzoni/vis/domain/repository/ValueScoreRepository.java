package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValueScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ValueScoreRepository extends JpaRepository<ValueScore, UUID> {
    Optional<ValueScore> findTopBySecurityOrderByScoreDateDescIdDesc(Security security);
    Optional<ValueScore> findTopBySecuritySymbolOrderByScoreDateDescIdDesc(String symbol);

    default Optional<ValueScore> findTopBySecurityOrderByScoreDateDesc(Security security) {
        return findTopBySecurityOrderByScoreDateDescIdDesc(security);
    }

    default Optional<ValueScore> findTopBySecuritySymbolOrderByScoreDateDesc(String symbol) {
        return findTopBySecuritySymbolOrderByScoreDateDescIdDesc(symbol);
    }

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from ValueScore v where v.security = :security and v.scoreDate = :scoreDate")
    void deleteBySecurityAndScoreDate(@Param("security") Security security,
                                      @Param("scoreDate") LocalDate scoreDate);
}
