package it.mazzoni.vis.domain.repository;

import it.mazzoni.vis.domain.entity.Alert;
import it.mazzoni.vis.domain.entity.AlertStatus;
import it.mazzoni.vis.domain.entity.AlertType;
import it.mazzoni.vis.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByUserAndStatus(User user, AlertStatus status);
    List<Alert> findByUserOrderByTriggeredAtDesc(User user);
    boolean existsByUserAndSymbolAndAlertTypeAndTriggeredAtBetween(User user, String symbol, AlertType alertType,
                                                                     LocalDateTime start, LocalDateTime end);
}
