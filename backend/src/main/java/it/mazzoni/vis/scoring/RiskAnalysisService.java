package it.mazzoni.vis.scoring;

import it.mazzoni.vis.domain.entity.*;
import it.mazzoni.vis.domain.repository.*;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class RiskAnalysisService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final PiotroskiResultRepository piotroskiResultRepository;
    private final AltmanResultRepository altmanResultRepository;
    private final CyclicalityResultRepository cyclicalityResultRepository;
    private final EarningsQualityResultRepository earningsQualityResultRepository;

    public RiskAnalysisService(SecurityRepository securityRepository,
                               FundamentalSnapshotRepository fundamentalSnapshotRepository,
                               RatioSnapshotRepository ratioSnapshotRepository,
                               PiotroskiResultRepository piotroskiResultRepository,
                               AltmanResultRepository altmanResultRepository,
                               CyclicalityResultRepository cyclicalityResultRepository,
                               EarningsQualityResultRepository earningsQualityResultRepository) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.piotroskiResultRepository = piotroskiResultRepository;
        this.altmanResultRepository = altmanResultRepository;
        this.cyclicalityResultRepository = cyclicalityResultRepository;
        this.earningsQualityResultRepository = earningsQualityResultRepository;
    }

    public PiotroskiResult computePiotroski(String symbol) {
        Security security = security(symbol);
        List<FundamentalSnapshot> annuals = annuals(security);
        List<RatioSnapshot> ratios = annualRatios(security);
        PiotroskiResult result = new PiotroskiResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        if (annuals.size() < 2) {
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("At least two annual fundamental snapshots are required.");
            return piotroskiResultRepository.save(result);
        }

        FundamentalSnapshot latest = annuals.get(0);
        FundamentalSnapshot prior = annuals.get(1);
        RatioSnapshot latestRatio = ratios.isEmpty() ? null : ratios.get(0);
        RatioSnapshot priorRatio = ratios.size() < 2 ? null : ratios.get(1);

        result.setPositiveNetIncome(positive(latest.getNetIncome()));
        result.setPositiveOperatingCashFlow(positive(latest.getOperatingCashFlow()));
        result.setImprovingRoa(compare(ratio(latest.getNetIncome(), latest.getTotalAssets()), ratio(prior.getNetIncome(), prior.getTotalAssets())) > 0);
        result.setCashFlowQuality(compare(latest.getOperatingCashFlow(), latest.getNetIncome()) > 0);
        result.setLowerLeverage(compare(ratio(latest.getTotalDebt(), latest.getTotalAssets()), ratio(prior.getTotalDebt(), prior.getTotalAssets())) < 0);
        result.setImprovingCurrentRatio(latestRatio != null && priorRatio != null && compare(latestRatio.getCurrentRatio(), priorRatio.getCurrentRatio()) > 0);
        result.setNoShareDilution(latest.getSharesOutstanding() != null && prior.getSharesOutstanding() != null
                && latest.getSharesOutstanding() <= prior.getSharesOutstanding());
        result.setImprovingGrossMargin(latestRatio != null && priorRatio != null && compare(latestRatio.getGrossMargin(), priorRatio.getGrossMargin()) > 0);
        result.setImprovingAssetTurnover(compare(ratio(latest.getRevenue(), latest.getTotalAssets()), ratio(prior.getRevenue(), prior.getTotalAssets())) > 0);
        result.setTotalScore(count(result));
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return piotroskiResultRepository.save(result);
    }

    public AltmanResult computeAltman(String symbol) {
        Security security = security(symbol);
        FundamentalSnapshot latest = fundamentalSnapshotRepository.findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL).orElse(null);
        RatioSnapshot ratio = ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM).stream().findFirst().orElse(null);
        AltmanResult result = new AltmanResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setFormulaVariant(isManufacturing(security) ? AltmanFormulaVariant.MANUFACTURING : AltmanFormulaVariant.NON_MANUFACTURING);
        if (latest == null || latest.getTotalAssets() == null || latest.getTotalAssets().compareTo(ZERO) == 0
                || latest.getTotalLiabilities() == null || latest.getTotalLiabilities().compareTo(ZERO) == 0) {
            result.setZone(AltmanZone.UNAVAILABLE);
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("Assets and liabilities are required for Altman Z-Score.");
            return altmanResultRepository.save(result);
        }
        BigDecimal x1 = workingCapitalToAssets(ratio, latest);
        BigDecimal x2 = ratio(latest.getNetIncome(), latest.getTotalAssets());
        BigDecimal x3 = ratio(latest.getOperatingIncome(), latest.getTotalAssets());
        BigDecimal x4 = ratio(security.getMarketCap(), latest.getTotalLiabilities());
        BigDecimal x5 = ratio(latest.getRevenue(), latest.getTotalAssets());
        result.setWorkingCapitalToAssets(x1);
        result.setRetainedEarningsToAssets(x2);
        result.setEbitToAssets(x3);
        result.setMarketValueEquityToLiabilities(x4);
        result.setSalesToAssets(x5);
        if (x1 == null || x2 == null || x3 == null || x4 == null || x5 == null) {
            result.setZone(AltmanZone.UNAVAILABLE);
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("Altman component inputs are incomplete.");
            return altmanResultRepository.save(result);
        }
        BigDecimal score = result.getFormulaVariant() == AltmanFormulaVariant.MANUFACTURING
                ? x1.multiply(new BigDecimal("1.2")).add(x2.multiply(new BigDecimal("1.4"))).add(x3.multiply(new BigDecimal("3.3"))).add(x4.multiply(new BigDecimal("0.6"))).add(x5)
                : x1.multiply(new BigDecimal("6.56")).add(x2.multiply(new BigDecimal("3.26"))).add(x3.multiply(new BigDecimal("6.72"))).add(x4.multiply(new BigDecimal("1.05")));
        result.setScore(score.setScale(4, RoundingMode.HALF_UP));
        result.setZone(zone(score));
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return altmanResultRepository.save(result);
    }

    public CyclicalityResult assessCyclicality(String symbol) {
        Security security = security(symbol);
        List<FundamentalSnapshot> annuals = annuals(security).stream().limit(10).toList();
        CyclicalityResult result = new CyclicalityResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setYearsAnalyzed(annuals.size());
        if (annuals.size() < 3) {
            result.setClassification(CyclicalityClassification.UNAVAILABLE);
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("At least three annual snapshots are required.");
            return cyclicalityResultRepository.save(result);
        }
        BigDecimal revenueCv = coefficient(annuals.stream().map(FundamentalSnapshot::getRevenue).toList());
        BigDecimal earningsCv = coefficient(annuals.stream().map(FundamentalSnapshot::getNetIncome).toList());
        BigDecimal normalized = average(annuals.stream().map(FundamentalSnapshot::getNetIncome).toList());
        result.setRevenueCoefficient(revenueCv);
        result.setEarningsCoefficient(earningsCv);
        result.setNormalizedEarnings(normalized);
        result.setCycleAdjustedPe(normalized != null && normalized.compareTo(ZERO) > 0 && security.getMarketCap() != null
                ? security.getMarketCap().divide(normalized, 4, RoundingMode.HALF_UP) : null);
        BigDecimal max = max(revenueCv, earningsCv);
        result.setClassification(max.compareTo(new BigDecimal("0.40")) >= 0 ? CyclicalityClassification.HIGHLY_CYCLICAL
                : max.compareTo(new BigDecimal("0.20")) >= 0 ? CyclicalityClassification.MODERATE : CyclicalityClassification.STABLE);
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return cyclicalityResultRepository.save(result);
    }

    public EarningsQualityResult computeEarningsQuality(String symbol) {
        Security security = security(symbol);
        List<FundamentalSnapshot> annuals = annuals(security).stream().limit(5).toList();
        EarningsQualityResult result = new EarningsQualityResult();
        result.setSecurity(security);
        result.setResultDate(LocalDate.now());
        result.setYearsAnalyzed(annuals.size());
        if (annuals.isEmpty()) {
            result.setClassification(EarningsQualityClassification.UNAVAILABLE);
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("Annual fundamentals are required.");
            return earningsQualityResultRepository.save(result);
        }
        FundamentalSnapshot latest = annuals.get(0);
        BigDecimal fcfToNi = ratio(latest.getFreeCashFlow(), latest.getNetIncome());
        BigDecimal accruals = latest.getNetIncome() != null && latest.getOperatingCashFlow() != null
                ? ratio(latest.getNetIncome().subtract(latest.getOperatingCashFlow()), latest.getTotalAssets()) : null;
        result.setFcfToNetIncome(fcfToNi);
        result.setSloanAccrualsRatio(accruals);
        if (fcfToNi == null) {
            result.setClassification(EarningsQualityClassification.UNAVAILABLE);
            result.setAvailabilityStatus(RiskAvailabilityStatus.INSUFFICIENT_DATA);
            result.setAvailabilityMessage("Free cash flow and net income are required.");
            return earningsQualityResultRepository.save(result);
        }
        result.setClassification(fcfToNi.compareTo(BigDecimal.ONE) >= 0 ? EarningsQualityClassification.STRONG
                : fcfToNi.compareTo(new BigDecimal("0.80")) >= 0 ? EarningsQualityClassification.ACCEPTABLE : EarningsQualityClassification.WEAK);
        if (annuals.size() >= 2) {
            FundamentalSnapshot prior = annuals.get(1);
            BigDecimal priorFcfToNi = ratio(prior.getFreeCashFlow(), prior.getNetIncome());
            BigDecimal priorAccruals = prior.getNetIncome() != null && prior.getOperatingCashFlow() != null
                    ? ratio(prior.getNetIncome().subtract(prior.getOperatingCashFlow()), prior.getTotalAssets()) : null;
            result.setDeteriorating(priorFcfToNi != null && priorAccruals != null
                    && compare(fcfToNi, priorFcfToNi) < 0 && compare(accruals, priorAccruals) > 0);
        }
        result.setAvailabilityStatus(RiskAvailabilityStatus.AVAILABLE);
        return earningsQualityResultRepository.save(result);
    }

    private Security security(String symbol) {
        return securityRepository.findBySymbol(symbol.toUpperCase()).orElseThrow(() -> new SymbolNotFoundException(symbol));
    }

    private List<FundamentalSnapshot> annuals(Security security) {
        return fundamentalSnapshotRepository.findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
    }

    private List<RatioSnapshot> annualRatios(Security security) {
        return ratioSnapshotRepository.findBySecurityAndPeriodOrderByReportDateDesc(security, Period.ANNUAL);
    }

    private int count(PiotroskiResult r) {
        int score = 0;
        if (r.isPositiveNetIncome()) score++;
        if (r.isPositiveOperatingCashFlow()) score++;
        if (r.isImprovingRoa()) score++;
        if (r.isCashFlowQuality()) score++;
        if (r.isLowerLeverage()) score++;
        if (r.isImprovingCurrentRatio()) score++;
        if (r.isNoShareDilution()) score++;
        if (r.isImprovingGrossMargin()) score++;
        if (r.isImprovingAssetTurnover()) score++;
        return score;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(ZERO) > 0;
    }

    private int compare(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return 0;
        }
        return left.compareTo(right);
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal workingCapitalToAssets(RatioSnapshot ratio, FundamentalSnapshot snapshot) {
        if (ratio == null || ratio.getCurrentRatio() == null || snapshot.getTotalAssets() == null) {
            return null;
        }
        BigDecimal currentRatio = ratio.getCurrentRatio();
        if (currentRatio.compareTo(ZERO) <= 0) {
            return null;
        }
        return currentRatio.subtract(BigDecimal.ONE).divide(currentRatio, 4, RoundingMode.HALF_UP);
    }

    private boolean isManufacturing(Security security) {
        String industry = security.getIndustry() != null ? security.getIndustry().toLowerCase() : "";
        String sector = security.getSector() != null ? security.getSector().toLowerCase() : "";
        return industry.contains("manufactur") || sector.contains("industrial");
    }

    private AltmanZone zone(BigDecimal score) {
        if (score.compareTo(new BigDecimal("2.99")) > 0) return AltmanZone.SAFE;
        if (score.compareTo(new BigDecimal("1.81")) >= 0) return AltmanZone.GREY;
        return AltmanZone.DISTRESS;
    }

    private BigDecimal coefficient(List<BigDecimal> values) {
        List<BigDecimal> clean = values.stream().filter(v -> v != null && v.compareTo(ZERO) != 0).toList();
        BigDecimal avg = average(clean);
        if (clean.size() < 2 || avg == null || avg.compareTo(ZERO) == 0) return ZERO;
        double mean = avg.doubleValue();
        double variance = clean.stream().mapToDouble(v -> Math.pow(v.doubleValue() - mean, 2)).sum() / clean.size();
        return BigDecimal.valueOf(Math.sqrt(variance) / Math.abs(mean)).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        List<BigDecimal> clean = values.stream().filter(v -> v != null).toList();
        if (clean.isEmpty()) return null;
        return clean.stream().reduce(ZERO, BigDecimal::add).divide(BigDecimal.valueOf(clean.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        if (left == null) return right != null ? right : ZERO;
        if (right == null) return left;
        return left.max(right);
    }
}
