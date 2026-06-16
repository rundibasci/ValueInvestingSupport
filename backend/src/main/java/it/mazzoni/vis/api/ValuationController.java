package it.mazzoni.vis.api;

import it.mazzoni.vis.api.dto.ValuationRequest;
import it.mazzoni.vis.api.dto.ValuationResponse;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.valuation.ValuationOutcome;
import it.mazzoni.vis.valuation.ValuationParams;
import it.mazzoni.vis.valuation.ValuationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
@Validated
public class ValuationController {

    private final ValuationService valuationService;

    public ValuationController(ValuationService valuationService) {
        this.valuationService = valuationService;
    }

    @PostMapping("/{symbol}/valuation/dcf")
    public ResponseEntity<ValuationResponse> runValuation(
            @PathVariable String symbol,
            @RequestBody @Valid ValuationRequest request) {

        ValuationParams params = new ValuationParams(
                request.wacc(),
                request.growthY1Y5(),
                request.growthY6Y10(),
                request.terminalRate(),
                request.requiredReturn(),
                request.dividendGrowthRate());

        ValuationOutcome outcome = valuationService.calculate(symbol, params);
        ValuationResult result = outcome.result();

        return ResponseEntity.ok(new ValuationResponse(
                result.getSecurity().getSymbol(),
                result.getValuationDate(),
                result.getDcfFairValue(),
                result.getDcfFairValueLow(),
                result.getDcfFairValueHigh(),
                result.getGrahamNumber(),
                result.getDdmFairValue(),
                result.getCompositeFairValue(),
                result.getCurrentPrice(),
                result.getMarginOfSafety(),
                result.getRecommendation(),
                ValuationResponse.DISCLAIMER,
                outcome.effectiveWeights()));
    }
}
