package it.mazzoni.vis.valuation;

import it.mazzoni.vis.config.ValuationWeightsProperties;
import it.mazzoni.vis.domain.entity.DividendRecord;
import it.mazzoni.vis.domain.entity.FundamentalSnapshot;
import it.mazzoni.vis.domain.entity.Period;
import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Recommendation;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.repository.DividendRecordRepository;
import it.mazzoni.vis.domain.repository.FundamentalSnapshotRepository;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.exception.SymbolNotFoundException;
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

    public ValuationService(
            SecurityRepository securityRepository,
            FundamentalSnapshotRepository fundamentalSnapshotRepository,
            DividendRecordRepository dividendRecordRepository,
            PriceQuoteRepository priceQuoteRepository,
            ValuationResultRepository valuationResultRepository,
            ValuationWeightsProperties weights) {
        this.securityRepository = securityRepository;
        this.fundamentalSnapshotRepository = fundamentalSnapshotRepository;
        this.dividendRecordRepository = dividendRecordRepository;
        this.priceQuoteRepository = priceQuoteRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.weights = weights;
    }

    public ValuationOutcome calculate(String symbol, ValuationParams params) {
        Security security = securityRepository.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new SymbolNotFoundException(symbol));

        FundamentalSnapshot snapshot = loadSnapshot(security);

        BigDecimal bvps = computeBvps(snapshot);
        BigDecimal netDebt = computeNetDebt(snapshot);
        BigDecimal fcfTtm = snapshot.getFreeCashFlow();
        int fcfYearsPositive = countFcfPositiveYears(security);

        BigDecimal grahamNumber = runGraham(snapshot.getEpsDiluted(), bvps);
        DcfResult dcfResult = runDcf(fcfTtm, snapshot.getSharesOutstanding(), netDebt, fcfYearsPositive, params);
        BigDecimal ddmFairValue = runDdm(security, params);

        BigDecimal dcfFairValue = dcfResult != null ? dcfResult.fairValue() : null;
        Map<String, BigDecimal> effectiveWeights = buildEffectiveWeights(dcfFairValue, grahamNumber, ddmFairValue, symbol);
        BigDecimal compositeFairValue = computeComposite(dcfFairValue, grahamNumber, ddmFairValue, effectiveWeights);

        BigDecimal currentPrice = priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)
                .map(PriceQuote::getClose)
                .orElse(null);

        BigDecimal mos = MarginOfSafetyCalculator.compute(compositeFairValue, currentPrice);
        Recommendation recommendation = deriveRecommendation(mos);

        ValuationResult result = new ValuationResult();
        result.setSecurity(security);
        result.setValuationDate(LocalDate.now());
        if (dcfResult != null) {
            result.setDcfFairValue(dcfResult.fairValue());
            result.setDcfFairValueLow(dcfResult.fairValueLow());
            result.setDcfFairValueHigh(dcfResult.fairValueHigh());
        }
        result.setGrahamNumber(grahamNumber);
        result.setDdmFairValue(ddmFairValue);
        result.setCompositeFairValue(compositeFairValue);
        result.setCurrentPrice(currentPrice);
        result.setMarginOfSafety(mos);
        result.setRecommendation(recommendation);
        result.setSource("fmp");

        return new ValuationOutcome(valuationResultRepository.save(result), effectiveWeights);
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

    private int countFcfPositiveYears(Security security) {
        return (int) fundamentalSnapshotRepository
                .findBySecurityAndPeriodOrderByFiscalYearDescFiscalQuarterDesc(security, Period.ANNUAL)
                .stream()
                .limit(3)
                .filter(s -> s.getFreeCashFlow() != null
                        && s.getFreeCashFlow().compareTo(BigDecimal.ZERO) > 0)
                .count();
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
            BigDecimal dcf, BigDecimal graham, BigDecimal ddm, String symbol) {
        Map<String, BigDecimal> configured = new LinkedHashMap<>();
        if (dcf != null) configured.put("dcf", weights.dcf());
        if (graham != null) configured.put("graham", weights.graham());
        if (ddm != null) configured.put("ddm", weights.ddm());

        if (configured.isEmpty()) {
            throw new ValuationNotApplicableException(symbol);
        }

        BigDecimal total = configured.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> effective = new LinkedHashMap<>();
        effective.put("dcf", dcf != null
                ? weights.dcf().divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        effective.put("graham", graham != null
                ? weights.graham().divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        effective.put("ddm", ddm != null
                ? weights.ddm().divide(total, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        return effective;
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
}
