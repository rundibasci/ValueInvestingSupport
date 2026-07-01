package it.mazzoni.vis.professional;

import it.mazzoni.vis.professional.dto.ResearchSnapshotResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/audit/decisions")
@Profile("!demo")
public class ResearchDecisionAuditController {
    private final ResearchDecisionAuditService service;

    public ResearchDecisionAuditController(ResearchDecisionAuditService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResearchSnapshotResponse> list(Authentication auth,
                                               @RequestParam(required = false) String symbol,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(auth, symbol, from, to);
    }
}
