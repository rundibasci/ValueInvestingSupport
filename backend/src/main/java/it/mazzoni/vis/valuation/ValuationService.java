package it.mazzoni.vis.valuation;

import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.config.ValuationEnhancementProperties;
import it.mazzoni.vis.config.ValuationWeightsProperties;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.GrahamChecklistItem;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationBandPosition;
import it.mazzoni.vis.domain.entity.ValuationBandResult;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.WaccResultEntity;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.GrahamChecklistItemRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.WaccResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
import it.mazzoni.vis.moat.ValuationHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class ValuationService {

    private final SecurityRepository securityRepository;
    private final FundamentalSnapshotRepository fundamentalSnapshotRepository;
    private final DividendRecordRepository dividendRecordRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final ValuationWeightsProperties weights;
    private final ValuationEnhancementProperties enhancementProperties;
    private final GrahamCriteriaService grahamCriteriaService;
    private final WaccResultRepository waccResultRepository;
    private final GrahamChecklistItemRepository grahamChecklistItemRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;
    private final ValuationHistoryService valuationHistoryService;

    public ValuationService(
            SecurityRepository securityRepository,
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            DividendRecordRepository dividendRecordRepository,
            PriceQuoteRepository priceQuoteRepository,
            ValuationResultRepository valuationResultRepository,
            ValuationWeightsProperties weights,
            ValuationEnhancementProperties enhancementProperties,
            RatioSnapshotRepository ratioSnapshotRepository,
            WaccResultRepository waccResultRepository,
            GrahamChecklistItemRepository grahamChecklistItemRepository,
            ValuationHistoryService valuationHistoryService) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.weights = weights;
        this.enhancementProperties = enhancementProperties;
        this.grahamCriteriaService = new GrahamCriteriaService(
                fundamentalSnapshotRepository, ratioSnapshotRepository, dividendRecordRepository);
        this.waccResultRepository = waccResultRepository;
        this.grahamChecklistItemRepository = grahamChecklistItemRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
        this.valuationHistoryService = valuationHistoryService;
    }

    public ValuationOutcome calculate(String symbol, ValuationParams params) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        FundamentalSnapshot snapshot = loadSnapshot(security);

        BigDecimal bvps = computeBvps(snapshot);
        BigDecimal netDebt = computeNetDebt(snapshot);
        BigDecimal fcfTtm = snapshot.getFreeCashFlow();
        List<FundamentalSnapshot> annualSnapshots = loadAnnualSnapshots(security);
        int fcfYearsPositive = countFcfPositiveYears(annualSnapshots);

        BigDecimal grahamNumber = runGraham(snapshot.getEpsDiluted(), bvps);
        DcfResult dcfResult = runDcf(fcfTtm, snapshot.getSharesOutstanding(), netDebt, fcfYearsPositive, params);
        BigDecimal ddmFairValue = runDdm(security, params);
        WaccResult waccResult = computeWacc(snapshot);
        EpvResult epvResult = runEpv(annualSnapshots, waccResult.wacc(), netDebt, snapshot.getSharesOutstanding());
        OwnerEarnings ownerEarnings = computeOwnerEarnings(snapshot);
        GrahamChecklistResult grahamChecklist = grahamCriteriaService.evaluate(security);

        BigDecimal dcfFairValue = dcfResult != null ? dcfResult.fairValue() : null;
        Map<String, BigDecimal> effectiveWeights = buildEffectiveWeights(
                dcfFairValue,
                grahamNumber,
                ddmFairValue,
                dcfResult != null && dcfResult.highTerminalDependence(),
                symbol);

        // RM5 (specs/2026-09-03-rm5-reit-composite-fair-value/): for a REIT, the headline
        // compositeFairValue/marginOfSafety come from an AFFO-based multiple instead of the
        // GAAP-anchored DCF/Graham/DDM blend below — dcfFairValue/grahamNumber/ddmFairValue are
        // still computed and persisted unchanged (still informative, still GAAP-based), they are
        // just no longer blended into this sector's headline number. affoFairValue is null
        // (never a silent fallback to the GAAP blend) when AFFO history is insufficient.
        BigDecimal affoFairValue = null;
        BigDecimal compositeFairValue;
        if (SectorClassifier.isReit(security.getSector())) {
            affoFairValue = deriveAffoFairValue(security);
            compositeFairValue = affoFairValue;
        } else {
            compositeFairValue = computeComposite(dcfFairValue, grahamNumber, ddmFairValue, effectiveWeights);
        }

        BigDecimal currentPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                .map(PriceQuote::getClose)
                .orElse(null);

        BigDecimal mos = MarginOfSafetyCalculator.compute(compositeFairValue, currentPrice);
        Recommendation recommendation = deriveRecommendation(mos);

        LocalDate valuationDate = LocalDate.now();
        valuationResultRepository.deleteBySecurityAndValuationDate(security, valuationDate);

        ValuationResult result = new ValuationResult();
        result.setSecurity(security);
        result.setValuationDate(valuationDate);
        if (dcfResult != null) {
            result.setDcfFairValue(dcfResult.fairValue());
            result.setDcfFairValueLow(dcfResult.fairValueLow());
            result.setDcfFairValueHigh(dcfResult.fairValueHigh());
            result.setDcfTerminalValuePercentage(dcfResult.terminalValuePercentage());
            result.setDcfHighTerminalDependence(dcfResult.highTerminalDependence());
        }
        result.setGrahamNumber(grahamNumber);
        result.setDdmFairValue(ddmFairValue);
        if (epvResult != null) {
            result.setEpvFairValue(epvResult.fairValue());
            result.setEpvNormalizedEarnings(epvResult.normalizedEarnings());
            result.setEpvYearsAveraged(epvResult.yearsAveraged());
        }
        result.setOwnerEarnings(ownerEarnings.value());
        result.setMaintenanceCapexEstimate(ownerEarnings.maintenanceCapex());
        result.setCompositeFairValue(compositeFairValue);
        result.setAffoFairValue(affoFairValue);
        result.setCurrentPrice(currentPrice);
        result.setMarginOfSafety(mos);
        result.setRecommendation(recommendation);
        result.setSource("fmp");

        ValuationResult saved = valuationResultRepository.save(result);
        persistWacc(saved, waccResult);
        persistGrahamChecklist(saved, grahamChecklist);

        return new ValuationOutcome(saved, effectiveWeights, waccResult, grahamChecklist);
    }

    private FundamentalSnapshot loadSnapshot(Security security) {
        Optional<FundamentalSnapshot> ttm = fundamentalSnapshotRepository
                .findTopBySecurityAndPeriodOrderByReportDateDesc(security, Period.TTM);
        if (ttm.isPresent()) {
            return ttm.get();
        }
        return fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ValuationDataUnavailableException(security.getSymbol()));
    }

    private List<FundamentalSnapshot> loadAnnualSnapshots(Security security) {
        return fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL);
    }

    private int countFcfPositiveYears(List<FundamentalSnapshot> annualSnapshots) {
        return (int) annualSnapshots
                .stream()
                .limit(3)
                .filter(s -> s.getFreeCashFlow() != null
                        && s.getFreeCashFlow().compareTo(BigDecimal.ZERO) > 0)
                .count();
    }

    /**
     * AFFO-based substitute fair value for a REIT-classified security (RM5): median historical
     * P/AFFO (this security's own trailing multiple, not a peer comparison — same "own history"
     * semantics as the P/FFO Value-Score pillar {@code ValueScoreService.computeMosScoreReit}
     * already uses) times the latest persisted AFFO per share. Computed live via {@link
     * ValuationHistoryService#compute(Security)} rather than read from a cached {@link
     * ValuationBandResult} row — same reasoning as {@code ValueScoreService.computeMosScoreReit}:
     * there is no ingestion-time job that populates {@code ValuationBandResult}, so a REIT's very
     * first valuation after seeding would otherwise find no persisted "P_AFFO" row at all.
     *
     * <p>Returns {@code null} — deliberately, never a fallback to the GAAP DCF/Graham/DDM blend —
     * when either input is unavailable: fewer than three years of {@code priceToAffo} history
     * ({@code P_AFFO} band {@code INSUFFICIENT_DATA}) or no persisted {@code
     * RatioSnapshot.affoPerShare} yet (REIT seeded before {@code SectorMetricService} ran). A
     * {@code null} return here means {@code compositeFairValue} and {@code marginOfSafety} are
     * {@code null} for this run — the whole point of RM5 ({@code
     * specs/2026-09-03-rm5-reit-composite-fair-value/requirements.md}, Decision 5) is that a
     * REIT's headline valuation output must never silently reuse the GAAP-anchored composite it
     * structurally distorts.
     */
    private BigDecimal deriveAffoFairValue(Security security) {
        ValuationBandResult pAffoBand = valuationHistoryService.compute(security).stream()
                .filter(b -> "P_AFFO".equals(b.getMetric()))
                .findFirst().orElse(null);
        if (pAffoBand == null || pAffoBand.getPosition() == ValuationBandPosition.INSUFFICIENT_DATA
                || pAffoBand.getMedianValue() == null) {
            return null;
        }
        BigDecimal affoPerShare = ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security)
                .map(RatioSnapshot::getAffoPerShare)
                .orElse(null);
        if (affoPerShare == null || affoPerShare.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return pAffoBand.getMedianValue().multiply(affoPerShare).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeBvps(FundamentalSnapshot snapshot) {
        BigDecimal equity = snapshot.getTotalEquity();
        Long shares = snapshot.getSharesOutstanding();
        if (equity == null || shares == null || shares <= 0) {
            return null;
        }
        return equity.divide(BigDecimal.valueOf(shares), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal computeNetDebt(FundamentalSnapshot snapshot) {
        BigDecimal debt = snapshot.getTotalDebt() != null ? snapshot.getTotalDebt() : BigDecimal.ZERO;
        BigDecimal cash = snapshot.getCash() != null ? snapshot.getCash() : BigDecimal.ZERO;
        return debt.subtract(cash);
    }

    private BigDecimal runGraham(BigDecimal eps, BigDecimal bvps) {
        try {
            return GrahamCalculator.calculate(eps, bvps);
        } catch (GrahamNotApplicableException e) {
            return null;
        }
    }

    private DcfResult runDcf(BigDecimal fcfTtm, Long sharesOutstanding, BigDecimal netDebt,
                              int fcfYearsPositive, ValuationParams params) {
        if (fcfTtm == null || sharesOutstanding == null || sharesOutstanding <= 0) {
            return null;
        }
        DcfInput input = new DcfInput(
                fcfTtm,
                params.growthY1Y5(),
                params.growthY6Y10(),
                params.terminalRate(),
                params.wacc(),
                BigDecimal.valueOf(sharesOutstanding),
                netDebt,
                fcfYearsPositive);
        try {
            return new DcfCalculator().calculate(input).orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BigDecimal runDdm(Security security, ValuationParams params) {
        if (params.requiredReturn() == null || params.dividendGrowthRate() == null) {
            return null;
        }
        LocalDate tenYearsAgo = LocalDate.now().minusYears(10);
        List<DividendRecord> records = dividendRecordRepository
                .findBySecurityOrderByExDividendDateDesc(security)
                .stream()
                .filter(r -> !r.getExDividendDate().isBefore(tenYearsAgo))
                .toList();

        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        BigDecimal dpsTtm = records.stream()
                .filter(r -> !r.getExDividendDate().isBefore(oneYearAgo))
                .map(DividendRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (dpsTtm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        int consecutiveYears = countConsecutiveDividendYears(records);

        try {
            return DdmCalculator.calculate(dpsTtm, params.dividendGrowthRate(),
                    params.requiredReturn(), consecutiveYears);
        } catch (DdmNotEligibleException | DdmNotApplicableException e) {
            return null;
        }
    }

    private WaccResult computeWacc(FundamentalSnapshot snapshot) {
        BigDecimal equity = snapshot.getTotalEquity();
        BigDecimal debt = snapshot.getTotalDebt();
        BigDecimal costOfDebt = null;
        if (debt != null && debt.compareTo(BigDecimal.ZERO) > 0 && snapshot.getOperatingIncome() != null
                && snapshot.getNetIncome() != null) {
            BigDecimal estimatedInterest = snapshot.getOperatingIncome().subtract(snapshot.getNetIncome());
            if (estimatedInterest.compareTo(BigDecimal.ZERO) > 0) {
                costOfDebt = estimatedInterest.divide(debt, 6, RoundingMode.HALF_UP);
            }
        }
        WaccInput input = new WaccInput(
                enhancementProperties.riskFreeRate(),
                enhancementProperties.equityRiskPremium(),
                null,
                costOfDebt,
                debt,
                equity,
                null,
                enhancementProperties.sectorFallbackWacc());
        return new WaccCalculator().compute(input);
    }

    private EpvResult runEpv(
            List<FundamentalSnapshot> annualSnapshots,
            BigDecimal wacc,
            BigDecimal netDebt,
            Long sharesOutstanding) {
        if (sharesOutstanding == null || sharesOutstanding <= 0) {
            return null;
        }
        List<BigDecimal> earnings = annualSnapshots.stream()
                .limit(7)
                .map(FundamentalSnapshot::getNetIncome)
                .filter(java.util.Objects::nonNull)
                .toList();
        return new EpvCalculator().calculate(new EpvInput(
                earnings, wacc, netDebt, BigDecimal.valueOf(sharesOutstanding))).orElse(null);
    }

    private OwnerEarnings computeOwnerEarnings(FundamentalSnapshot snapshot) {
        OwnerEarningsCalculator calculator = new OwnerEarningsCalculator();
        BigDecimal depreciation = null;
        if (snapshot.getOperatingCashFlow() != null && snapshot.getNetIncome() != null) {
            BigDecimal estimate = snapshot.getOperatingCashFlow().subtract(snapshot.getNetIncome());
            depreciation = estimate.compareTo(BigDecimal.ZERO) > 0 ? estimate : BigDecimal.ZERO;
        }
        BigDecimal maintenanceCapex = calculator.estimateMaintenanceCapex(
                depreciation, enhancementProperties.maintenanceCapexDepreciationRatio());
        return new OwnerEarnings(calculator.calculate(snapshot.getNetIncome(), depreciation, maintenanceCapex),
                maintenanceCapex);
    }

    private int countConsecutiveDividendYears(List<DividendRecord> records) {
        int currentYear = LocalDate.now().getYear();
        int consecutive = 0;
        for (int y = currentYear; y >= currentYear - 10; y--) {
            final int year = y;
            boolean hasDividend = records.stream()
                    .anyMatch(r -> r.getExDividendDate().getYear() == year);
            if (hasDividend) {
                consecutive++;
            } else {
                break;
            }
        }
        return consecutive;
    }

    private Map<String, BigDecimal> buildEffectiveWeights(
            BigDecimal dcf, BigDecimal graham, BigDecimal ddm, boolean highTerminalDependence, String symbol) {
        Map<String, BigDecimal> configured = new LinkedHashMap<>();
        BigDecimal dcfWeight = weights.dcf();
        BigDecimal grahamWeight = weights.graham();
        BigDecimal ddmWeight = weights.ddm();

        validateWeights(dcfWeight, grahamWeight, ddmWeight);

        if (highTerminalDependence && dcf != null && dcfWeight.compareTo(enhancementProperties.reducedDcfWeight()) > 0) {
            BigDecimal shift = dcfWeight.subtract(enhancementProperties.reducedDcfWeight());
            dcfWeight = enhancementProperties.reducedDcfWeight();
            BigDecimal redistributionBase = BigDecimal.ZERO;
            if (graham != null) redistributionBase = redistributionBase.add(grahamWeight);
            if (ddm != null) redistributionBase = redistributionBase.add(ddmWeight);
            if (redistributionBase.compareTo(BigDecimal.ZERO) > 0) {
                if (graham != null) {
                    grahamWeight = grahamWeight.add(shift.multiply(grahamWeight)
                            .divide(redistributionBase, 6, RoundingMode.HALF_UP));
                }
                if (ddm != null) {
                    ddmWeight = ddmWeight.add(shift.multiply(ddmWeight)
                            .divide(redistributionBase, 6, RoundingMode.HALF_UP));
                }
            }
        }

        if (dcf != null) configured.put("dcf", dcfWeight);
        if (graham != null) configured.put("graham", grahamWeight);
        if (ddm != null) configured.put("ddm", ddmWeight);

        if (configured.isEmpty()) {
            throw new ValuationNotApplicableException(symbol);
        }

        BigDecimal total = configured.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> effective = new LinkedHashMap<>();
        effective.put("dcf", dcf != null
                ? dcfWeight.divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        effective.put("graham", graham != null
                ? grahamWeight.divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        effective.put("ddm", ddm != null
                ? ddmWeight.divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        return effective;
    }

    private void validateWeights(BigDecimal dcf, BigDecimal graham, BigDecimal ddm) {
        BigDecimal total = dcf.add(graham).add(ddm);
        if (total.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Composite valuation weights must sum to 1.00");
        }
    }

    private BigDecimal computeComposite(
            BigDecimal dcf, BigDecimal graham, BigDecimal ddm,
            Map<String, BigDecimal> effectiveWeights) {
        BigDecimal composite = BigDecimal.ZERO;
        if (dcf != null) composite = composite.add(dcf.multiply(effectiveWeights.get("dcf")));
        if (graham != null) composite = composite.add(graham.multiply(effectiveWeights.get("graham")));
        if (ddm != null) composite = composite.add(ddm.multiply(effectiveWeights.get("ddm")));
        return composite.setScale(2, RoundingMode.HALF_UP);
    }

    private Recommendation deriveRecommendation(BigDecimal mos) {
        if (mos == null) return null;
        if (mos.compareTo(new BigDecimal("25")) >= 0) return Recommendation.STRONG_BUY;
        if (mos.compareTo(new BigDecimal("10")) >= 0) return Recommendation.QUALITY_VALUE;
        if (mos.compareTo(BigDecimal.ZERO) >= 0) return Recommendation.FAIR_VALUE;
        return Recommendation.OVERVALUED;
    }

    private void persistWacc(ValuationResult valuationResult, WaccResult waccResult) {
        WaccResultEntity entity = new WaccResultEntity();
        entity.setValuationResult(valuationResult);
        entity.setWacc(waccResult.wacc());
        entity.setRiskFreeRate(waccResult.riskFreeRate());
        entity.setEquityRiskPremium(waccResult.equityRiskPremium());
        entity.setBeta(waccResult.beta());
        entity.setCostOfEquity(waccResult.costOfEquity());
        entity.setCostOfDebt(waccResult.costOfDebt());
        entity.setDebtWeight(waccResult.debtWeight());
        entity.setEquityWeight(waccResult.equityWeight());
        entity.setEffectiveTaxRate(waccResult.effectiveTaxRate());
        entity.setFallbackUsed(waccResult.fallbackUsed());
        entity.setSource(waccResult.source());
        waccResultRepository.save(entity);
    }

    private void persistGrahamChecklist(ValuationResult valuationResult, GrahamChecklistResult checklist) {
        for (GrahamCriterionResult criterion : checklist.criteria()) {
            GrahamChecklistItem item = new GrahamChecklistItem();
            item.setValuationResult(valuationResult);
            item.setCriterionCode(criterion.code());
            item.setLabel(criterion.label());
            item.setStatus(criterion.status().name());
            item.setActualValue(criterion.actualValue());
            grahamChecklistItemRepository.save(item);
        }
    }

    private record OwnerEarnings(BigDecimal value, BigDecimal maintenanceCapex) {}
}
