package it.mazzoni.vis.common;

import it.mazzoni.vis.common.dto.AvailabilityDiagnosticResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/availability")
@Profile("!demo")
public class AvailabilityDiagnosticsController {

    private final AvailabilityDiagnosticsService service;

    public AvailabilityDiagnosticsController(AvailabilityDiagnosticsService service) {
        this.service = service;
    }

    @GetMapping("/diagnostics")
    public List<AvailabilityDiagnosticResponse> diagnostics() {
        return service.all();
    }
}
