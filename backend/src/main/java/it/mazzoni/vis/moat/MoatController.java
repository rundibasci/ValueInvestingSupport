package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.CapitalAllocationResult;
import it.mazzoni.vis.domain.entity.MoatResult;
import it.mazzoni.vis.domain.entity.StabilityResult;
import it.mazzoni.vis.domain.entity.ValuationBandResult;
import it.mazzoni.vis.domain.repository.StabilityResultRepository;
import it.mazzoni.vis.moat.dto.CapitalAllocationResponse;
import it.mazzoni.vis.moat.dto.MoatResponse;
import it.mazzoni.vis.moat.dto.ValuationBandsResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class MoatController {
    private final MoatAssessmentService moatAssessmentService;
    private final CapitalAllocationService capitalAllocationService;
    private final ValuationHistoryService valuationHistoryService;
    private final StabilityResultRepository stabilityResultRepository;

    public MoatController(MoatAssessmentService moatAssessmentService,
                          CapitalAllocationService capitalAllocationService,
                          ValuationHistoryService valuationHistoryService,
                          StabilityResultRepository stabilityResultRepository) {
        this.moatAssessmentService = moatAssessmentService;
        this.capitalAllocationService = capitalAllocationService;
        this.valuationHistoryService = valuationHistoryService;
        this.stabilityResultRepository = stabilityResultRepository;
    }

    @GetMapping("/{symbol}/moat")
    public ResponseEntity<MoatResponse> moat(@PathVariable String symbol) {
        MoatResult result = moatAssessmentService.analyze(symbol);
        List<StabilityResult> criteria = stabilityResultRepository.findBySecurityAndResultDateOrderByCriterionCodeAsc(
                result.getSecurity(), result.getResultDate());
        return ResponseEntity.ok(MoatResponse.from(result, criteria));
    }

    @GetMapping("/{symbol}/capital-allocation")
    public ResponseEntity<CapitalAllocationResponse> capitalAllocation(@PathVariable String symbol) {
        CapitalAllocationResult result = capitalAllocationService.analyze(symbol);
        return ResponseEntity.ok(CapitalAllocationResponse.from(result));
    }

    @GetMapping("/{symbol}/valuation-bands")
    public ResponseEntity<ValuationBandsResponse> valuationBands(@PathVariable String symbol) {
        List<ValuationBandResult> results = valuationHistoryService.compute(symbol);
        return ResponseEntity.ok(ValuationBandsResponse.from(symbol.toUpperCase(), results));
    }
}
