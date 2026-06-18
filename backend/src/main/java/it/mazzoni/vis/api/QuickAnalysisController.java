package it.mazzoni.vis.api;

import it.mazzoni.vis.api.dto.QuickAnalysisResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class QuickAnalysisController {

    private final QuickAnalysisService quickAnalysisService;

    public QuickAnalysisController(QuickAnalysisService quickAnalysisService) {
        this.quickAnalysisService = quickAnalysisService;
    }

    @GetMapping("/{symbol}/quick-analysis")
    public ResponseEntity<QuickAnalysisResponse> quickAnalysis(@PathVariable String symbol) {
        return ResponseEntity.ok(quickAnalysisService.analyze(symbol.toUpperCase()));
    }
}
