package it.mazzoni.vis.alerts;

import it.mazzoni.vis.watchlist.dto.AlertResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@Profile("!demo")
public class AlertController {
    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PutMapping("/{id}/ack")
    public AlertResponse acknowledge(Authentication authentication, @PathVariable UUID id) {
        return alertService.acknowledge(authentication, id);
    }
}
