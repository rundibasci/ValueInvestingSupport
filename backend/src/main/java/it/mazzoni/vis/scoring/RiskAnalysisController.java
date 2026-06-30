package it.mazzoni.vis.scoring;

import it.mazzoni.vis.scoring.dto.AltmanResponse;
import it.mazzoni.vis.scoring.dto.CyclicalityResponse;
import it.mazzoni.vis.scoring.dto.EarningsQualityResponse;
import it.mazzoni.vis.scoring.dto.PiotroskiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class RiskAnalysisController {
    private final RiskAnalysisService riskAnalysisService;

    public RiskAnalysisController(RiskAnalysisService riskAnalysisService) {
        this.riskAnalysisService = riskAnalysisService;
    }

    @GetMapping("/{symbol}/piotroski")
    public ResponseEntity<PiotroskiResponse> piotroski(@PathVariable String symbol) {
        return ResponseEntity.ok(PiotroskiResponse.from(riskAnalysisService.computePiotroski(symbol)));
    }

    @GetMapping("/{symbol}/altman")
    public ResponseEntity<AltmanResponse> altman(@PathVariable String symbol) {
        return ResponseEntity.ok(AltmanResponse.from(riskAnalysisService.computeAltman(symbol)));
    }

    @GetMapping("/{symbol}/cyclicality")
    public ResponseEntity<CyclicalityResponse> cyclicality(@PathVariable String symbol) {
        return ResponseEntity.ok(CyclicalityResponse.from(riskAnalysisService.assessCyclicality(symbol)));
    }

    @GetMapping("/{symbol}/earnings-quality")
    public ResponseEntity<EarningsQualityResponse> earningsQuality(@PathVariable String symbol) {
        return ResponseEntity.ok(EarningsQualityResponse.from(riskAnalysisService.computeEarningsQuality(symbol)));
    }
}
