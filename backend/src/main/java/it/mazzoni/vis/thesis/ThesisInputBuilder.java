package it.mazzoni.vis.thesis;

import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds {@link ThesisInput} from the platform's existing valuation/scoring/market-data
 * layers — reuses {@link MarketDataClient} (cache-first, mission.md Principle 5),
 * {@link ValuationResultRepository}, {@link ValueScoreRepository} unchanged.
 *
 * <p><b>New derivation, not a reuse of existing logic</b> (flagged explicitly, TA4 session
 * decision, 2026-08-28): {@code netDebtToEbitda} and the three trend fields
 * (revenue/earnings/free-cash-flow) had no equivalent anywhere else in this codebase before
 * this class. EBITDA is approximated from {@code operatingIncomeHistory} (no separate
 * depreciation/amortization figure is captured anywhere in {@link FundamentalSnapshot}) —
 * documented here as an approximation, not a precise EBITDA calculation, and this
 * approximation must never leak into any deterministic VIS calculation (DCF/Graham/DDM/Value
 * Score) — it exists solely to feed the AI thesis interpretation layer, per mission.md
 * Principle 15's "never computes... itself" boundary. Trend thresholds are a first-pass,
 * documented heuristic (see {@link #classifyTrend}) — review before relying on it as more
 * than a reasonable default; not derived from any existing VIS specification.
 */
@Component
public class ThesisInputBuilder implements ThesisInputSource {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("15");
    private static final BigDecimal MILD_THRESHOLD = new BigDecimal("3");
    private static final BigDecimal VOLATILITY_THRESHOLD = new BigDecimal("50");

    private final MarketDataClient marketDataClient;
    private final ValuationResultRepository valuationResultRepository;
    private final ValueScoreRepository valueScoreRepository;

    public ThesisInputBuilder(MarketDataClient marketDataClient,
                              ValuationResultRepository valuationResultRepository,
                              ValueScoreRepository valueScoreRepository) {
        this.marketDataClient = marketDataClient;
        this.valuationResultRepository = valuationResultRepository;
        this.valueScoreRepository = valueScoreRepository;
    }

    @Override
    public ThesisInput build(Security security) {
        List<String> warnings = new ArrayList<>();

        FundamentalSnapshot fundamentals = safeFundamentals(security.getSymbol(), warnings);
        RatioSnapshot ratios = safeRatios(security.getSymbol(), warnings);
        Optional<ValuationResult> valuation = valuationResultRepository.findTopBySecurityOrderByValuationDateDesc(security);
        Optional<ValueScore> valueScore = valueScoreRepository.findTopBySecurityOrderByScoreDateDesc(security);

        BigDecimal marketPrice = valuation.map(ValuationResult::getCurrentPrice).orElse(null);
        BigDecimal intrinsicValue = valuation.map(ValuationResult::getCompositeFairValue).orElse(null);
        BigDecimal marginOfSafety = valuation.map(ValuationResult::getMarginOfSafety).orElse(null);
        BigDecimal totalScore = valueScore.map(ValueScore::getTotalScore).orElse(null);

        if (marketPrice == null) warnings.add("marketPrice unavailable: no ValuationResult on record");
        if (intrinsicValue == null) warnings.add("intrinsicValue unavailable: no ValuationResult composite fair value on record");
        if (totalScore == null) warnings.add("valueScore unavailable: no ValueScore on record");

        Trend revenueTrend = classifyTrend(fundamentals != null ? fundamentals.revenueHistory() : null, "revenue", warnings);
        Trend earningsTrend = classifyTrend(fundamentals != null ? fundamentals.netIncomeHistory() : null, "earnings", warnings);
        Trend fcfTrend = classifyTrend(fundamentals != null ? fundamentals.fcfHistory() : null, "freeCashFlow", warnings);

        BigDecimal netDebtToEbitda = deriveNetDebtToEbitda(fundamentals, warnings);

        DataQuality dataQuality = deriveDataQuality(marketPrice, intrinsicValue, totalScore, warnings);

        return new ThesisInput(
                security.getSymbol(),
                security.getCompanyName(),
                LocalDate.now(),
                marketPrice,
                intrinsicValue,
                marginOfSafety,
                totalScore,
                ratios != null ? ratios.dividendYield() : null,
                ratios != null ? ratios.payoutRatio() : null,
                netDebtToEbitda,
                revenueTrend,
                earningsTrend,
                fcfTrend,
                dataQuality,
                warnings
        );
    }

    private FundamentalSnapshot safeFundamentals(String symbol, List<String> warnings) {
        try {
            return marketDataClient.getFundamentals(symbol);
        } catch (Exception e) {
            warnings.add("fundamentals unavailable: " + e.getMessage());
            return null;
        }
    }

    private RatioSnapshot safeRatios(String symbol, List<String> warnings) {
        try {
            return marketDataClient.getRatios(symbol);
        } catch (Exception e) {
            warnings.add("ratios unavailable: " + e.getMessage());
            return null;
        }
    }

    /** First-pass heuristic, documented: YoY change on the last two points of the series;
     * &gt;=15% strongly growing, &gt;=3% growing, within +-3% stable, &lt;=-15% strongly
     * declining, &lt;=-3% declining. With 3+ points, a period-over-period swing exceeding 50%
     * anywhere in the series overrides to VOLATILE regardless of the latest direction. Fewer
     * than two points, or a zero/undefined prior-period base, yields NOT_AVAILABLE rather than
     * a fabricated classification. */
    Trend classifyTrend(List<BigDecimal> history, String label, List<String> warnings) {
        if (history == null || history.size() < 2) {
            warnings.add(label + "Trend unavailable: fewer than 2 historical data points");
            return Trend.NOT_AVAILABLE;
        }
        List<BigDecimal> periodChanges = new ArrayList<>();
        for (int i = 1; i < history.size(); i++) {
            BigDecimal prev = history.get(i - 1);
            BigDecimal curr = history.get(i);
            if (prev == null || curr == null || prev.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            periodChanges.add(curr.subtract(prev).divide(prev.abs(), 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        }
        if (periodChanges.isEmpty()) {
            warnings.add(label + "Trend unavailable: no comparable non-zero historical periods");
            return Trend.NOT_AVAILABLE;
        }
        boolean volatile_ = periodChanges.size() >= 2 && periodChanges.stream()
                .anyMatch(change -> change.abs().compareTo(VOLATILITY_THRESHOLD) > 0);
        if (volatile_) {
            return Trend.VOLATILE;
        }
        BigDecimal latest = periodChanges.get(periodChanges.size() - 1);
        if (latest.compareTo(STRONG_THRESHOLD) >= 0) return Trend.STRONGLY_GROWING;
        if (latest.compareTo(MILD_THRESHOLD) >= 0) return Trend.GROWING;
        if (latest.compareTo(MILD_THRESHOLD.negate()) >= 0) return Trend.STABLE;
        if (latest.compareTo(STRONG_THRESHOLD.negate()) >= 0) return Trend.DECLINING;
        return Trend.STRONGLY_DECLINING;
    }

    /** netDebt / latest operating income — an EBITDA *approximation* (no separate D&A figure
     * is captured anywhere in FundamentalSnapshot); documented, not precise. Never feeds any
     * deterministic VIS calculation, only this AI-interpretation-layer input. */
    private BigDecimal deriveNetDebtToEbitda(FundamentalSnapshot fundamentals, List<String> warnings) {
        if (fundamentals == null || fundamentals.netDebt() == null
                || fundamentals.operatingIncomeHistory() == null || fundamentals.operatingIncomeHistory().isEmpty()) {
            warnings.add("netDebtToEbitda unavailable: netDebt or operating income history missing");
            return null;
        }
        List<BigDecimal> operatingIncome = fundamentals.operatingIncomeHistory();
        BigDecimal latestOperatingIncome = operatingIncome.get(operatingIncome.size() - 1);
        if (latestOperatingIncome == null || latestOperatingIncome.compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add("netDebtToEbitda unavailable: latest operating income (EBITDA approximation) is missing or non-positive");
            return null;
        }
        return fundamentals.netDebt().divide(latestOperatingIncome, 2, RoundingMode.HALF_UP);
    }

    private DataQuality deriveDataQuality(BigDecimal marketPrice, BigDecimal intrinsicValue,
                                          BigDecimal totalScore, List<String> warnings) {
        if (marketPrice == null || intrinsicValue == null) {
            return DataQuality.INSUFFICIENT;
        }
        if (totalScore == null || !warnings.isEmpty()) {
            return DataQuality.PARTIAL;
        }
        return DataQuality.COMPLETE;
    }
}
