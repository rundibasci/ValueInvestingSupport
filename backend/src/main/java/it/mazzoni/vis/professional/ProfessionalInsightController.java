package it.mazzoni.vis.professional;

import it.mazzoni.vis.professional.dto.ConfidenceResponse;
import it.mazzoni.vis.professional.dto.VerificationResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/professional")
@Profile("!demo")
public class ProfessionalInsightController {
    private final ValuationConfidenceService confidenceService;
    private final DataVerificationService verificationService;

    public ProfessionalInsightController(ValuationConfidenceService confidenceService, DataVerificationService verificationService) {
        this.confidenceService = confidenceService;
        this.verificationService = verificationService;
    }

    @GetMapping("/valuation-confidence/{symbol}")
    public ConfidenceResponse confidence(@PathVariable String symbol) {
        return confidenceService.compute(symbol);
    }

    @GetMapping("/data-verification/{symbol}")
    public VerificationResponse verification(@PathVariable String symbol) {
        return verificationService.check(symbol);
    }
}
