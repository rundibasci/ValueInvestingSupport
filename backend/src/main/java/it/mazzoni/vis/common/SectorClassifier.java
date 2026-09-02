package it.mazzoni.vis.common;

/**
 * Shared sector classification used wherever the platform needs to know whether a security
 * belongs to a sector whose classic GAAP-earnings metrics (P/E, ROE/ROIC, Debt/Equity, EPS
 * payout ratio) are known to be structurally distorted — real estate depreciation is large and
 * non-cash, and REITs/utilities are leveraged by business model rather than by choice.
 *
 * <p>This is the single source of truth for that classification: previously the same
 * string-matching lived only inside {@code ValueScoreService.determineWeightProfile}, used to
 * pick a scoring weight profile. It is extracted here (RM0, {@code
 * specs/sector-aware-valuation-metrics.md}) so the scoring weight profile and the interim
 * screener/security-detail caveat below share one classification instead of two copies that
 * could drift apart. Sector-appropriate replacement metrics themselves (FFO, AFFO, Debt/EBITDA)
 * are a later phase (RM1+) — this class only answers "does the caveat/reweighting apply?".
 */
public final class SectorClassifier {

    /** Design Principle 16 (mission.md) — the interim caveat shown wherever a REIT/real-estate
     * or utility security's GAAP-earnings metrics are displayed, until RM1+ ships sector-aware
     * replacements (FFO, AFFO, Debt/EBITDA). Reuses the existing MiFID-disclaimer render pattern
     * rather than a new UI convention. */
    public static final String REIT_UTILITY_METRIC_CAVEAT =
            "P/E, ROE/ROIC, and Debt/Equity are known to be less reliable for REIT/real-estate and "
            + "utility stocks: non-cash real-estate depreciation depresses GAAP earnings, and these "
            + "sectors are structurally leveraged by business model. Sector-aware metrics (FFO, AFFO, "
            + "Debt/EBITDA) are planned but not yet available — see specs/sector-aware-valuation-metrics.md.";

    private SectorClassifier() {
    }

    /** True for REIT, real-estate, and utility sector strings (case-insensitive substring match,
     * matching FMP's free-text sector field) — unchanged from the matching previously private to
     * {@code ValueScoreService.determineWeightProfile}. */
    public static boolean isReitOrUtility(String sector) {
        if (sector == null) {
            return false;
        }
        String lower = sector.toLowerCase();
        return lower.contains("real estate") || lower.contains("reit") || lower.contains("utilit");
    }

    /**
     * True for REIT/real-estate sector strings only — deliberately excludes utilities.
     *
     * <p>RM2 ({@code specs/sector-aware-valuation-metrics.md} §10, open question 4) gates the
     * {@code SectorMetricProfile} (FFO/AFFO/P-FFO/P-AFFO/Debt-EBITDA computation) on this narrower
     * classification, separate from {@link #isReitOrUtility}, which continues to gate only the
     * RM0 caveat and the {@code "reit-utility"} scoring weight-profile key. A utility security is
     * structurally leveraged for different reasons than a REIT and has no FFO/AFFO concept — it
     * must keep its GAAP metrics until a dedicated utility {@code SectorMetricProfile} is scoped,
     * so it must never start rendering REIT-shaped fields just because it shares the weight
     * profile's sector-string match.
     */
    public static boolean isReit(String sector) {
        if (sector == null) {
            return false;
        }
        String lower = sector.toLowerCase();
        return lower.contains("real estate") || lower.contains("reit");
    }
}
