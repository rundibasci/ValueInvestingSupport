package it.mazzoni.vis.admin;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seed/runs")
@Profile("!demo")
public class SeedRunController {
    private final SeedRunService service;
    public SeedRunController(SeedRunService service) { this.service = service; }

    @GetMapping("/{id}")
    public SeedRunStatusResponse status(Authentication auth, @PathVariable UUID id) { return service.status(auth, id); }

    @GetMapping("/{id}/outcomes")
    public PageResponse<SeedRunOutcomeResponse> outcomes(Authentication auth, @PathVariable UUID id,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        return service.outcomes(auth, id, page, size);
    }

    @PostMapping("/{id}/retry-failures")
    public SeedRunAcceptedResponse retry(Authentication auth, @PathVariable UUID id) {
        return service.retryFailures(auth, id);
    }
}
