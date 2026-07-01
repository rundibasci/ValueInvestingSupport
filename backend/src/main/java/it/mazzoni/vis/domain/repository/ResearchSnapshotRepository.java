package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.ResearchSnapshot;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ResearchSnapshotRepository extends JpaRepository<ResearchSnapshot, UUID> {
    List<ResearchSnapshot> findByUserAndSymbolIgnoreCaseAndCapturedAtBetweenOrderByCapturedAtDesc(User user, String symbol, LocalDateTime from, LocalDateTime to);
    List<ResearchSnapshot> findByUserAndCapturedAtBetweenOrderByCapturedAtDesc(User user, LocalDateTime from, LocalDateTime to);
    List<ResearchSnapshot> findBySymbolIgnoreCaseAndCapturedAtBetweenOrderByCapturedAtDesc(String symbol, LocalDateTime from, LocalDateTime to);
    List<ResearchSnapshot> findByCapturedAtBetweenOrderByCapturedAtDesc(LocalDateTime from, LocalDateTime to);
}
