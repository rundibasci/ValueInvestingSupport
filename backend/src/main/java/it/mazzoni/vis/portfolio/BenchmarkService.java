package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.portfolio.dto.BenchmarkComparisonResponse;
import it.mazzoni.vis.portfolio.dto.WeightedMetricsResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class BenchmarkService {
    private final SecurityRepository securities;
    private final RatioSnapshotRepository ratios;
    private final ValuationResultRepository valuations;

    public BenchmarkService(SecurityRepository securities, RatioSnapshotRepository ratios,
                            ValuationResultRepository valuations) {
        this.securities = securities;
        this.ratios = ratios;
        this.valuations = valuations;
    }

    public BenchmarkComparisonResponse compare(WeightedMetricsResponse portfolioMetrics,
                                               Map<String, BigDecimal> portfolioSectorWeights,
                                               String benchmarkSymbol) {
        String resolvedBenchmark = benchmarkSymbol == null || benchmarkSymbol.isBlank()
                ? "SPY"
                : benchmarkSymbol.toUpperCase();
        return securities.findBySymbol(resolvedBenchmark)
                .map(security -> availableComparison(security, portfolioMetrics, portfolioSectorWeights))
                .orElse(new BenchmarkComparisonResponse(resolvedBenchmark,
                        portfolioMetrics.peRatio(), null,
                        portfolioMetrics.dividendYield(), null,
                        portfolioMetrics.marginOfSafety(), null,
                        Map.of(), "BENCHMARK_DATA_UNAVAILABLE"));
    }

    private BenchmarkComparisonResponse availableComparison(Security security, WeightedMetricsResponse portfolioMetrics,
                                                            Map<String, BigDecimal> portfolioSectorWeights) {
        RatioSnapshot ratio = ratios.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream().findFirst()
                .orElseGet(() -> ratios.findTopBySecurityOrderByReportDateDesc(security).orElse(null));
        ValuationResult valuation = valuations.findTopBySecurityOrderByValuationDateDesc(security).orElse(null);
        BigDecimal benchmarkSectorWeight = BigDecimal.valueOf(100);
        Map<String, BigDecimal> differences = new HashMap<>();
        if (security.getSector() != null) {
            differences.put(security.getSector(), portfolioSectorWeights.getOrDefault(security.getSector(), BigDecimal.ZERO)
                    .subtract(benchmarkSectorWeight).setScale(2, RoundingMode.HALF_UP));
        }
        return new BenchmarkComparisonResponse(security.getSymbol(),
                portfolioMetrics.peRatio(), ratio == null ? null : ratio.getPeRatio(),
                portfolioMetrics.dividendYield(), ratio == null ? null : ratio.getDividendYield(),
                portfolioMetrics.marginOfSafety(), valuation == null ? null : valuation.getMarginOfSafety(),
                differences, "AVAILABLE");
    }
}
