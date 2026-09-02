package it.mazzoni.vis.security.dto;

import it.mazzoni.vis.common.dto.AvailabilityResponse;
import it.mazzoni.vis.domain.entity.RatioSnapshot;

import java.math.BigDecimal;

/**
 * RM3 (specs/2026-09-02-rm3-screener-security-detail-surfacing/) — surfaces the seven REIT
 * sector metrics RM2's {@code SectorMetricService} already computes and persists on {@link
 * RatioSnapshot}. Populated only for {@code SectorClassifier.isReit} securities; {@code null}
 * for every other sector (see {@code SecurityReviewService.getReview}).
 *
 * <p>Each formula string is a static per-metric-group constant, mirroring {@code
 * DerivedRoicCalculator.FORMULA}'s existing precedent (Design Principle 2 — every valuation
 * output shows its formula and inputs, nothing is a black box) — not a new convention.
 */
public record SectorMetricResponse(
        BigDecimal ffoPerShare,
        String ffoFormula,
        BigDecimal affoPerShare,
        String affoFormula,
        BigDecimal priceToFfo,
        BigDecimal priceToAffo,
        String valuationMultipleFormula,
        BigDecimal netDebtToEbitda,
        BigDecimal interestCoverageEbitda,
        String safetyFormula,
        BigDecimal affoPayoutRatio,
        String payoutFormula,
        AvailabilityResponse availability
) {
    static final String FFO_FORMULA =
            "FFO per share = (Net Income + Depreciation & Amortization) / Shares Outstanding — a "
            + "D&A-only approximation of the full NAREIT formula (gains/losses on real-estate sales "
            + "are not isolable from FMP's data; see specs/sector-aware-valuation-metrics.md §5).";

    static final String AFFO_FORMULA =
            "AFFO per share = FFO per share − recurring capex per share, where recurring capex is "
            + "estimated as Depreciation & Amortization × 70% (the platform's existing maintenance-"
            + "capex assumption, reused from the DCF owner-earnings calculation) — a known "
            + "conservative-AFFO understatement for net-lease REITs, see "
            + "specs/2026-09-02-rm2-sector-metric-profile/validation.md → Known Risks.";

    static final String VALUATION_MULTIPLE_FORMULA =
            "P/FFO and P/AFFO = current price ÷ FFO or AFFO per share — REIT-appropriate "
            + "substitutes for P/E; lower is cheaper relative to the metric's own history, not "
            + "relative to an intrinsic-value margin of safety.";

    static final String SAFETY_FORMULA =
            "Net Debt/EBITDA = (Total Debt − Cash) / EBITDA; EBITDA Interest Coverage = EBITDA / "
            + "Interest Expense — substitutes for Debt/Equity and generic interest coverage. Uses "
            + "raw (unadjusted) EBITDA, not a REIT's own EBITDAre adjustment, so this reads more "
            + "conservatively than a company-reported leverage figure — see "
            + "specs/2026-09-02-rm2-sector-metric-profile/validation.md → Known Risks.";

    static final String PAYOUT_FORMULA =
            "AFFO Payout Ratio = (GAAP Payout Ratio × EPS) / AFFO per share — substitutes for EPS "
            + "payout ratio as the dividend-sustainability check for this sector.";

    public static SectorMetricResponse from(RatioSnapshot ratio) {
        if (ratio == null || allNull(ratio)) {
            return new SectorMetricResponse(
                    null, FFO_FORMULA,
                    null, AFFO_FORMULA,
                    null, null, VALUATION_MULTIPLE_FORMULA,
                    null, null, SAFETY_FORMULA,
                    null, PAYOUT_FORMULA,
                    AvailabilityResponse.missingComputation(
                            "Sector-aware REIT metrics have not yet been computed for this security "
                            + "— reseed or wait for the next score computation."));
        }
        return new SectorMetricResponse(
                ratio.getFfoPerShare(), FFO_FORMULA,
                ratio.getAffoPerShare(), AFFO_FORMULA,
                ratio.getPriceToFfo(), ratio.getPriceToAffo(), VALUATION_MULTIPLE_FORMULA,
                ratio.getNetDebtToEbitda(), ratio.getInterestCoverageEbitda(), SAFETY_FORMULA,
                ratio.getAffoPayoutRatio(), PAYOUT_FORMULA,
                AvailabilityResponse.available(ratio.getReportDate()));
    }

    private static boolean allNull(RatioSnapshot ratio) {
        return ratio.getFfoPerShare() == null
                && ratio.getAffoPerShare() == null
                && ratio.getPriceToFfo() == null
                && ratio.getPriceToAffo() == null
                && ratio.getNetDebtToEbitda() == null
                && ratio.getInterestCoverageEbitda() == null
                && ratio.getAffoPayoutRatio() == null;
    }
}
