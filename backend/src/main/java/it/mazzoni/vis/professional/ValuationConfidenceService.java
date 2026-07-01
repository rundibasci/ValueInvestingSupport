package it.mazzoni.vis.professional;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.professional.dto.ConfidenceResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class ValuationConfidenceService {
    private final SecurityRepository securities;
    private final FundamentalSnapshotRepository fundamentals;
    private final ValuationResultRepository valuations;
    private final RatioSnapshotRepository ratios;

    public ValuationConfidenceService(SecurityRepository securities, FundamentalSnapshotRepository fundamentals,
                                      ValuationResultRepository valuations, RatioSnapshotRepository ratios) {
        this.securities = securities;
        this.fundamentals = fundamentals;
        this.valuations = valuations;
        this.ratios = ratios;
    }

    public ConfidenceResponse compute(String symbol) {
        String upper = symbol.toUpperCase();
        Security security = securities.findBySymbol(upper).orElse(null);
        if (security == null) {
            return new ConfidenceResponse(upper, "LOW", List.of(new ConfidenceResponse.Factor("seeded-history", "LOW", "Symbol is not seeded.")));
        }
        List<ConfidenceResponse.Factor> factors = new ArrayList<>();
        int years = fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL).size();
        factors.add(factor("historical-data", years >= 10 ? "HIGH" : years >= 5 ? "MEDIUM" : "LOW", years + " annual snapshots available."));
        ValuationResult valuation = valuations.findTopBySecurityOrderByValuationDateDesc(security).orElse(null);
        factors.add(spreadFactor(valuation));
        factors.add(modelFactor(valuation));
        boolean dataComplete = fundamentals.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .map(f -> f.getEps() != null && f.getFreeCashFlow() != null && f.getSharesOutstanding() != null)
                .orElse(false)
                && ratios.findTopBySecurityOrderByReportDateDesc(security).isPresent();
        factors.add(factor("data-completeness", dataComplete ? "HIGH" : "LOW", dataComplete ? "Critical valuation inputs are present." : "One or more critical inputs are missing."));
        factors.add(earningsFactor(security));
        return new ConfidenceResponse(upper, aggregate(factors), factors);
    }

    private ConfidenceResponse.Factor spreadFactor(ValuationResult valuation) {
        if (valuation == null || valuation.getDcfFairValue() == null || valuation.getDcfFairValueLow() == null
                || valuation.getDcfFairValueHigh() == null || valuation.getDcfFairValue().compareTo(BigDecimal.ZERO) == 0) {
            return factor("dcf-spread", "LOW", "DCF scenario spread is unavailable.");
        }
        BigDecimal spread = valuation.getDcfFairValueHigh().subtract(valuation.getDcfFairValueLow()).abs()
                .divide(valuation.getDcfFairValue().abs(), 4, RoundingMode.HALF_UP);
        return factor("dcf-spread", spread.compareTo(new BigDecimal("0.20")) < 0 ? "HIGH" :
                spread.compareTo(new BigDecimal("0.40")) <= 0 ? "MEDIUM" : "LOW", "Scenario spread ratio is " + spread + ".");
    }

    private ConfidenceResponse.Factor modelFactor(ValuationResult valuation) {
        if (valuation == null) return factor("valuation-models", "LOW", "No valuation result is available.");
        int count = 0;
        if (valuation.getDcfFairValue() != null) count++;
        if (valuation.getGrahamNumber() != null) count++;
        if (valuation.getDdmFairValue() != null) count++;
        if (valuation.getEpvFairValue() != null) count++;
        return factor("valuation-models", count >= 3 ? "HIGH" : count == 2 ? "MEDIUM" : "LOW", count + " valuation models are available.");
    }

    private ConfidenceResponse.Factor earningsFactor(Security security) {
        List<BigDecimal> earnings = fundamentals.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream().limit(5).map(FundamentalSnapshot::getNetIncome).filter(v -> v != null).toList();
        if (earnings.size() < 3) return factor("earnings-consistency", "LOW", "Fewer than three earnings observations are available.");
        long positive = earnings.stream().filter(v -> v.compareTo(BigDecimal.ZERO) > 0).count();
        return factor("earnings-consistency", positive == earnings.size() ? "HIGH" : positive >= earnings.size() - 1 ? "MEDIUM" : "LOW",
                positive + " of " + earnings.size() + " recent annual earnings observations are positive.");
    }

    private ConfidenceResponse.Factor factor(String name, String level, String message) {
        return new ConfidenceResponse.Factor(name, level, message);
    }

    private String aggregate(List<ConfidenceResponse.Factor> factors) {
        long lows = factors.stream().filter(f -> "LOW".equals(f.level())).count();
        long highs = factors.stream().filter(f -> "HIGH".equals(f.level())).count();
        if (lows >= 2) return "LOW";
        return highs >= 3 ? "HIGH" : "MEDIUM";
    }
}
