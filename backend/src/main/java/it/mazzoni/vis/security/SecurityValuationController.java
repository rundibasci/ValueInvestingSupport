package it.mazzoni.vis.security;

import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.security.domain.AnalystEstimate;
import it.mazzoni.vis.security.domain.AnalystEstimateRepository;
import it.mazzoni.vis.security.dto.AnalystEstimatesItem;
import it.mazzoni.vis.security.dto.DcfScenarios;
import it.mazzoni.vis.security.dto.ValuationDetailResponse;
import it.mazzoni.vis.valuation.MarginOfSafetyCalculator;
import it.mazzoni.vis.valuation.ValuationDataUnavailableException;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/securities")
@Profile("!demo")
public class SecurityValuationController {

    private final SecurityRepository securityRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final AnalystEstimateRepository analystEstimateRepository;

    public SecurityValuationController(SecurityRepository securityRepository,
                                       ValuationResultRepository valuationResultRepository,
                                       AnalystEstimateRepository analystEstimateRepository) {
        this.securityRepository = securityRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.analystEstimateRepository = analystEstimateRepository;
    }

    @GetMapping("/{symbol}/valuation")
    public ResponseEntity<ValuationDetailResponse> valuation(@PathVariable String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        ValuationResult result = valuationResultRepository
                .findTopBySecurityOrderByValuationDateDesc(security)
                .orElseThrow(() -> new ValuationDataUnavailableException(symbol));

        AnalystEstimatesItem analystEstimates = buildAnalystEstimates(symbol);

        BigDecimal mosLow = computeMos(result.getDcfFairValueLow(), result.getCurrentPrice());
        BigDecimal mosHigh = computeMos(result.getDcfFairValueHigh(), result.getCurrentPrice());

        DcfScenarios dcf = new DcfScenarios(
                result.getDcfFairValue(),
                result.getDcfFairValueLow(),
                result.getDcfFairValueHigh()
        );

        return ResponseEntity.ok(new ValuationDetailResponse(
                security.getSymbol(),
                security.getCompanyName(),
                result.getCurrentPrice(),
                dcf,
                result.getDcfTerminalValuePercentage(),
                result.isDcfHighTerminalDependence(),
                null,
                result.getGrahamNumber(),
                result.getDdmFairValue(),
                result.getEpvFairValue() != null
                        ? new ValuationDetailResponse.EpvDetail(result.getEpvFairValue(), result.getEpvNormalizedEarnings(), result.getEpvYearsAveraged())
                        : null,
                result.getOwnerEarnings() != null
                        ? new ValuationDetailResponse.OwnerEarningsDetail(result.getOwnerEarnings(), result.getMaintenanceCapexEstimate())
                        : null,
                result.getCompositeFairValue(),
                result.getMarginOfSafety(),
                mosLow,
                mosHigh,
                result.getRecommendation() != null ? result.getRecommendation().name() : null,
                analystEstimates,
                null,
                null,
                result.getValuationDate(),
                ValuationDetailResponse.MIFID_DISCLAIMER
        ));
    }

    private BigDecimal computeMos(BigDecimal fairValue, BigDecimal currentPrice) {
        if (fairValue == null || currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) == 0) return null;
        return MarginOfSafetyCalculator.compute(fairValue, currentPrice);
    }

    private AnalystEstimatesItem buildAnalystEstimates(String symbol) {
        List<AnalystEstimate> estimates = analystEstimateRepository
                .findBySecuritySymbolOrderByTargetDateDesc(symbol.toUpperCase());

        if (estimates.isEmpty()) return null;

        List<BigDecimal> prices = estimates.stream()
                .map(AnalystEstimate::getTargetPrice)
                .filter(p -> p != null)
                .toList();

        if (prices.isEmpty()) return null;

        BigDecimal mean = prices.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(prices.size()), 2, RoundingMode.HALF_UP);
        BigDecimal low = prices.stream().min(Comparator.naturalOrder()).orElse(null);
        BigDecimal high = prices.stream().max(Comparator.naturalOrder()).orElse(null);

        Map<String, Long> ratingCounts = estimates.stream()
                .filter(e -> e.getRatingLabel() != null)
                .collect(Collectors.groupingBy(AnalystEstimate::getRatingLabel, Collectors.counting()));

        String consensus = resolveConsensus(ratingCounts);

        return new AnalystEstimatesItem(mean, low, high, estimates.size(), consensus);
    }

    private String resolveConsensus(Map<String, Long> counts) {
        for (String rating : List.of("BUY", "HOLD", "SELL")) {
            long count = counts.getOrDefault(rating, 0L);
            long others = counts.values().stream().mapToLong(Long::longValue).sum() - count;
            if (count > others) return rating;
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
