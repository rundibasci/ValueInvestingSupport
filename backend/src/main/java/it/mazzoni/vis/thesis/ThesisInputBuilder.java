package it.mazzoni.vis.thesis;

import it.mazzoni.vis.common.SectorClassifier;
import it.mazzoni.vis.domain.FundamentalSnapshot;
import it.mazzoni.vis.domain.RatioSnapshot;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.entity.ValuationResult;
import it.mazzoni.vis.domain.entity.ValueScore;
import it.mazzoni.vis.domain.repository.RatioSnapshotRepository;
import it.mazzoni.vis.domain.repository.ValuationResultRepository;
import it.mazzoni.vis.domain.repository.ValueScoreRepository;
import it.mazzoni.vis.marketdata.MarketDataClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds {@link ThesisInput} from the platform's existing valuation/scoring/market-data
 * layers — reuses {@link MarketDataClient} (cache-first, mission.md Principle 5),
 * {@link ValuationResultRepository}, {@link ValueScoreRepository} unchanged.
 *
 * <p><b>New derivation, not a reuse of existing logic</b> (flagged explicitly, TA4 session
 * decision, 2026-08-28): the three trend fields (revenue/earnings/free-cash-flow) had no
 * equivalent anywhere else in this codebase before this class. Trend thresholds are a
 * first-pass, documented heuristic (see {@link #classifyTrend}) — review before relying on
 * it as more than a reasonable default; not derived from any existing VIS specification.
 *
 * <p><b>{@code netDebtToEbitda} (RM4):</b> reads {@link FundamentalSnapshot#ebitdaHistory()},
 * RM1's precise, FMP-computed EBITDA figure — no longer an approximation now that a real
 * EBITDA field exists platform-wide (not REIT-gated; see
 * {@code specs/2026-09-02-rm4-ai-thesis-propagation/requirements.md} Decision 3). Never feeds
 * any deterministic VIS calculation (DCF/Graham/DDM/Value Score), only this AI-interpretation-
 * layer input, per mission.md Principle 15's "never computes... itself" boundary.
 *
 * <p><b>The five REIT fields (RM4, {@code ffoPerShare}/{@code affoPerShare}/{@code priceToFfo}/
 * {@code priceToAffo}/{@code affoPayoutRatio}):</b> zero new computation here — read directly
 * off RM2's already-computed {@code RatioSnapshot} entity columns
 * ({@link RatioSnapshotRepository}, same access path {@code SecurityReviewService} already
 * uses) for {@link SectorClassifier#isReit(String) REIT-classified} securities only. Non-REIT
 * securities never query {@link RatioSnapshotRepository} at all.
 */
@Component
public class ThesisInputBuilder implements ThesisInputSource {

    private static final BigDecimal STRONG_THRESHOLD = new BigDecimal("15");
    private static final BigDecimal MILD_THRESHOLD = new BigDecimal("3");
    private static final BigDecimal VOLATILITY_THRESHOLD = new BigDecimal("50");

    private final MarketDataClient marketDataClient;
    private final ValuationResultRepository valuationResultRepository;
    private final ValueScoreRepository valueScoreRepository;
    private final RatioSnapshotRepository ratioSnapshotRepository;

    public ThesisInputBuilder(MarketDataClient marketDataClient,
                              ValuationResultRepository valuationResultRepository,
                              ValueScoreRepository valueScoreRepository,
                              RatioSnapshotRepository ratioSnapshotRepository) {
        this.marketDataClient = marketDataClient;
        this.valuationResultRepository = valuationResultRepository;
        this.valueScoreRepository = valueScoreRepository;
        this.ratioSnapshotRepository = ratioSnapshotRepository;
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

        it.mazzoni.vis.domain.entity.RatioSnapshot reitRatios = SectorClassifier.isReit(security.getSector())
                ? ratioSnapshotRepository.findTopBySecurityOrderByReportDateDesc(security).orElse(null)
                : null;

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
                reitRatios != null ? reitRatios.getFfoPerShare() : null,
                reitRatios != null ? reitRatios.getAffoPerShare() : null,
                reitRatios != null ? reitRatios.getPriceToFfo() : null,
                reitRatios != null ? reitRatios.getPriceToAffo() : null,
                reitRatios != null ? reitRatios.getAffoPayoutRatio() : null,
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
     * a fabricated classification.
     *
     * <p><b>{@code history} is newest-first</b> (index 0 = most recent), the same convention
     * confirmed platform-wide for every {@code FundamentalSnapshot} {@code *History} list (see
     * {@link #deriveNetDebtToEbitda}'s Javadoc below, {@code FmpAdapter.toFundamentalSnapshot},
     * and {@code DemoAnalysisService}). This method reverses it into chronological (oldest-first)
     * order before computing period-over-period changes, so "the latest period" below correctly
     * means the most recent one, not index {@code history.size() - 1} (TA6: prior to this fix,
     * this method made the opposite assumption from every other reader of these lists — see
     * {@code specs/2026-09-03-ta6-thesis-trend-direction-fix/requirements.md}). */
    Trend classifyTrend(List<BigDecimal> history, String label, List<String> warnings) {
        if (history == null || history.size() < 2) {
            warnings.add(label + "Trend unavailable: fewer than 2 historical data points");
            return Trend.NOT_AVAILABLE;
        }
        List<BigDecimal> chronological = new ArrayList<>(history);
        Collections.reverse(chronological);
        List<BigDecimal> periodChanges = new ArrayList<>();
        for (int i = 1; i < chronological.size(); i++) {
            BigDecimal prev = chronological.get(i - 1);
            BigDecimal curr = chronological.get(i);
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

    /** netDebt / latest EBITDA ({@link FundamentalSnapshot#ebitdaHistory()}, RM1's precise,
     * FMP-computed figure — no longer an operating-income approximation as of RM4). Never
     * feeds any deterministic VIS calculation, only this AI-interpretation-layer input.
     *
     * <p><b>Index 0, not the last index, is "latest"</b> (RM4, found via this phase's own
     * live smoke test against real seeded FMP data for {@code O}: the {@code size()-1}
     * indexing this method previously used — inherited from the pre-RM4
     * {@code operatingIncomeHistory}-based version — silently picked the *oldest* year in a
     * ~7-year history, producing a materially wrong ratio, e.g. ~35x instead of the correct
     * ~9x for {@code O}). {@code FmpAdapter.toFundamentalSnapshot}'s every *History list is
     * built from FMP's own newest-first statement ordering ({@code income.get(0)} is read as
     * "TTM/current" for {@code epsTtm}/{@code totalDebt}/{@code cash}/{@code shares}
     * elsewhere in that same method), and {@code SeedTickerService.persistFundamentals} only
     * falls back to the domain record's scalar {@code totalDebt()}/{@code cash()} at
     * {@code i == 0} — both confirm index 0, not the last index, is the most recent period. */
    private BigDecimal deriveNetDebtToEbitda(FundamentalSnapshot fundamentals, List<String> warnings) {
        if (fundamentals == null || fundamentals.netDebt() == null
                || fundamentals.ebitdaHistory() == null || fundamentals.ebitdaHistory().isEmpty()) {
            warnings.add("netDebtToEbitda unavailable: netDebt or EBITDA history missing");
            return null;
        }
        BigDecimal latestEbitda = fundamentals.ebitdaHistory().get(0);
        if (latestEbitda == null || latestEbitda.compareTo(BigDecimal.ZERO) <= 0) {
            warnings.add("netDebtToEbitda unavailable: latest EBITDA is missing or non-positive");
            return null;
        }
        return fundamentals.netDebt().divide(latestEbitda, 2, RoundingMode.HALF_UP);
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
