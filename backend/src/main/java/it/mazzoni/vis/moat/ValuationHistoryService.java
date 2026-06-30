package it.mazzoni.vis.moat;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationBandResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

@Service
public class ValuationHistoryService {
    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final ValuationBandResultRepository valuationBandResultRepository;

    public ValuationHistoryService(SecurityRepository securityRepository,
                                   RatioSnapshotRepository ratioSnapshotRepository,
                                   ValuationBandResultRepository valuationBandResultRepository) {
        this.securityRepository = securityRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.valuationBandResultRepository = valuationBandResultRepository;
    }

    @Transactional
    public List<ValuationBandResult> compute(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
        return compute(security);
    }

    @Transactional
    public List<ValuationBandResult> compute(Security security) {
        List<RatioSnapshot> annuals = ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream().limit(10).toList();
        LocalDate resultDate = LocalDate.now();
        List<ValuationBandResult> bands = List.of(
                band(security, resultDate, "PE", annuals, RatioSnapshot::getPeRatio, false),
                band(security, resultDate, "PB", annuals, RatioSnapshot::getPbRatio, false),
                band(security, resultDate, "EV_EBITDA", annuals, RatioSnapshot::getEvToEbitda, false),
                band(security, resultDate, "DIVIDEND_YIELD", annuals, RatioSnapshot::getDividendYield, true)
        );
        valuationBandResultRepository.deleteBySecurity(security);
        return valuationBandResultRepository.saveAll(bands);
    }

    private ValuationBandResult band(Security security, LocalDate date, String metric, List<RatioSnapshot> annuals,
                                     Function<RatioSnapshot, BigDecimal> extractor, boolean higherIsCheap) {
        List<BigDecimal> values = annuals.stream().map(extractor).map(v -> higherIsCheap ? MoatMath.normalizeRatio(v) : v)
                .filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0)
                .sorted()
                .toList();
        ValuationBandResult result = new ValuationBandResult();
        result.setSecurity(security);
        result.setResultDate(date);
        result.setMetric(metric);
        result.setYearsAnalyzed(values.size());
        if (values.size() < 3) {
            result.setPosition(ValuationBandPosition.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("At least three historical observations are required.");
            return result;
        }
        BigDecimal current = higherIsCheap ? MoatMath.normalizeRatio(extractor.apply(annuals.get(0))) : extractor.apply(annuals.get(0));
        result.setCurrentValue(current);
        result.setPercentile25(percentile(values, 0.25));
        result.setMedianValue(percentile(values, 0.50));
        result.setPercentile75(percentile(values, 0.75));
        result.setCurrentPercentile(currentPercentile(values, current));
        result.setPosition(position(current, result.getPercentile25(), result.getPercentile75(), higherIsCheap));
        return result;
    }

    private BigDecimal percentile(List<BigDecimal> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }

    private BigDecimal currentPercentile(List<BigDecimal> sorted, BigDecimal current) {
        if (current == null) return null;
        long belowOrEqual = sorted.stream().filter(v -> v.compareTo(current) <= 0).count();
        return BigDecimal.valueOf(belowOrEqual).divide(BigDecimal.valueOf(sorted.size()), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
    }

    private ValuationBandPosition position(BigDecimal current, BigDecimal p25, BigDecimal p75, boolean higherIsCheap) {
        if (current == null || p25 == null || p75 == null) return ValuationBandPosition.INSUFFICIENT_DATA;
        if (higherIsCheap) {
            if (current.compareTo(p75) > 0) return ValuationBandPosition.HISTORICALLY_CHEAP;
            if (current.compareTo(p25) < 0) return ValuationBandPosition.HISTORICALLY_EXPENSIVE;
            return ValuationBandPosition.NORMAL;
        }
        if (current.compareTo(p25) < 0) return ValuationBandPosition.HISTORICALLY_CHEAP;
        if (current.compareTo(p75) > 0) return ValuationBandPosition.HISTORICALLY_EXPENSIVE;
        return ValuationBandPosition.NORMAL;
    }
}
