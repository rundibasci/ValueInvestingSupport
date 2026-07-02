package it.mazzoni.vis.screener;

import it.mazzoni.vis.screener.dto.ConservativeComparisonRowResponse;
import it.mazzoni.vis.screener.dto.ConservativeEmptyStateDiagnosticResponse;
import it.mazzoni.vis.screener.dto.ConservativePresetResponse;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conservative-workflow")
@Profile("!demo")
public class ConservativeWorkflowController {

    private final ConservativeWorkflowService service;

    public ConservativeWorkflowController(ConservativeWorkflowService service) {
        this.service = service;
    }

    @GetMapping("/preset")
    public ConservativePresetResponse preset() {
        return service.preset();
    }

    @PostMapping("/empty-state-diagnostics")
    public ConservativeEmptyStateDiagnosticResponse emptyStateDiagnostics(@RequestBody ScreenerRequest request) {
        return service.emptyStateDiagnostics(request);
    }

    @GetMapping("/agent-one-comparison")
    public List<ConservativeComparisonRowResponse> agentOneComparison() {
        return service.agentOneComparison();
    }
}
