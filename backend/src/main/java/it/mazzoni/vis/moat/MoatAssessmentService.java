package it.mazzoni.vis.moat;

import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MoatAssessmentService {
    private final SecurityRepository securityRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final WaccResultRepository waccResultRepository;
    private final MoatResultRepository moatResultRepository;
    private final StabilityService stabilityService;
    private final ValuationEnhancementProperties valuationEnhancementProperties;

    public MoatAssessmentService(SecurityRepository securityRepository,
                                 RatioSnapshotRepository ratioSnapshotRepository,
                                 FundamentalSnapshotRepository fundamentalSnapshotRepository,
                                 ValuationResultRepository valuationResultRepository,
                                 WaccResultRepository waccResultRepository,
                                 MoatResultRepository moatResultRepository,
                                 StabilityService stabilityService,
                                 ValuationEnhancementProperties valuationEnhancementProperties) {
        this.securityRepository = securityRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.waccResultRepository = waccResultRepository;
        this.moatResultRepository = moatResultRepository;
        this.stabilityService = stabilityService;
        this.valuationEnhancementProperties = valuationEnhancementProperties;
    }

    @Transactional
    public MoatResult analyze(String symbol) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));
        MoatResult result = analyze(security);
        stabilityService.assess(security);
        return result;
    }

    @Transactional
    public MoatResult analyze(Security security) {
        List<RatioSnapshot> ratios = ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL)
                .stream().filter(r -> r.getRoic() != null).limit(10).toList();
        BigDecimal wacc = latestWacc(security);
        MoatResult result = new MoatResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setEstimatedWacc(wacc);
        result.setYearsAnalyzed(ratios.size());

        if (ratios.size() < 5) {
            result.setMoatStrength(MoatStrength.INSUFFICIENT_DATA);
            result.setRoicTrend(RoicTrend.INSUFFICIENT_DATA);
            result.setYearsRoicAboveWacc(0);
            result.setAvailabilityMessage("At least five annual ROIC observations are required.");
        } else {
            List<BigDecimal> roicValues = ratios.stream().map(RatioSnapshot::getRoic).map(MoatMath::normalizeRatio).toList();
            int aboveWacc = (int) roicValues.stream().filter(v -> v.compareTo(wacc) > 0).count();
            BigDecimal averageRoic = MoatMath.avg(roicValues);
            BigDecimal spread = averageRoic != null ? averageRoic.subtract(wacc) : null;
            List<BigDecimal> chronological = new ArrayList<>(roicValues);
            java.util.Collections.reverse(chronological);
            BigDecimal slope = MoatMath.slope(chronological);
            RoicTrend trend = classifyTrend(slope);
            result.setYearsRoicAboveWacc(aboveWacc);
            result.setRoicConsistencyPercentage(BigDecimal.valueOf(aboveWacc)
                    .divide(BigDecimal.valueOf(roicValues.size()), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP));
            result.setAverageRoic(averageRoic);
            result.setAverageRoicSpread(spread);
            result.setTrendSlope(slope);
            result.setRoicTrend(trend);
            result.setMoatStrength(classifyMoat(aboveWacc, trend));
            result.setReinvestmentRate(reinvestmentRate(security));
            result.setAvailabilityMessage(null);
        }

        moatResultRepository.deleteBySecurity(security);
        return moatResultRepository.save(result);
    }

    private BigDecimal latestWacc(Security security) {
        return valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security)
                .flatMap(waccResultRepository::findByValuationResult)
                .map(WaccResultEntity::getWacc)
                .filter(w -> w.compareTo(BigDecimal.ZERO) > 0)
                .orElse(valuationEnhancementProperties.sectorFallbackWacc());
    }

    private RoicTrend classifyTrend(BigDecimal slope) {
        if (slope == null) return RoicTrend.INSUFFICIENT_DATA;
        if (slope.compareTo(new BigDecimal("0.005")) > 0) return RoicTrend.IMPROVING;
        if (slope.compareTo(new BigDecimal("-0.005")) < 0) return RoicTrend.DECLINING;
        return RoicTrend.STABLE;
    }

    private MoatStrength classifyMoat(int aboveWacc, RoicTrend trend) {
        if (aboveWacc >= 8 && trend != RoicTrend.DECLINING) return MoatStrength.WIDE;
        if (aboveWacc >= 5) return MoatStrength.NARROW;
        return MoatStrength.NONE;
    }

    private BigDecimal reinvestmentRate(Security security) {
        List<FundamentalSnapshot> annuals = fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream().limit(3).toList();
        List<BigDecimal> rates = annuals.stream()
                .map(a -> {
                    if (a.getOperatingCashFlow() == null || a.getFreeCashFlow() == null || a.getNetIncome() == null
                            || a.getNetIncome().compareTo(BigDecimal.ZERO) <= 0) return null;
                    BigDecimal capex = a.getOperatingCashFlow().subtract(a.getFreeCashFlow()).abs();
                    return capex.divide(a.getNetIncome(), 6, RoundingMode.HALF_UP);
                })
                .filter(v -> v != null)
                .toList();
        return MoatMath.avg(rates);
    }
}
