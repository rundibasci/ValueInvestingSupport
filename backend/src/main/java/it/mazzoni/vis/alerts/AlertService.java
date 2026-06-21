package it.mazzoni.vis.alerts;

import it.mazzoni.vis.domain.entity.Alert;
import it.mazzoni.vis.domain.entity.AlertStatus;
import it.mazzoni.vis.domain.entity.User;
import it.mazzoni.vis.domain.repository.AlertRepository;
import it.mazzoni.vis.domain.repository.UserRepository;
import it.mazzoni.vis.watchlist.dto.AlertResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AlertService {
    private final AlertRepository alerts;
    private final UserRepository users;

    public AlertService(AlertRepository alerts, UserRepository users) {
        this.alerts = alerts;
        this.users = users;
    }

    @Transactional
    public AlertResponse acknowledge(Authentication authentication, UUID id) {
        User user = users.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Alert alert = alerts.findById(id)
                .filter(candidate -> candidate.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));
        if (alert.getStatus() == AlertStatus.ACTIVE) {
            alert.setStatus(AlertStatus.ACKNOWLEDGED);
            alert.setAcknowledgedAt(LocalDateTime.now());
            alerts.save(alert);
        }
        return AlertResponse.from(alert);
    }
}
