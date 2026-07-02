package it.mazzoni.vis.screener;

import it.mazzoni.vis.screener.dto.ConservativeComparisonMetricResponse;
import it.mazzoni.vis.screener.dto.ConservativeComparisonRowResponse;
import it.mazzoni.vis.screener.dto.ConservativeCriterionResponse;
import it.mazzoni.vis.screener.dto.ConservativeEmptyStateDiagnosticResponse;
import it.mazzoni.vis.screener.dto.ConservativePresetResponse;
import it.mazzoni.vis.screener.dto.ScreenerRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConservativeWorkflowService {

    private static final String DECISION_SUPPORT_NOTE =
            "Use these signals to structure research; they do not recommend transactions or position changes in any security.";

    public ConservativePresetResponse preset() {
        return new ConservativePresetResponse(
                "conservative",
                "Positive valuation gap, available score evidence, dividend signal, balance-sheet resilience, and complete data coverage.",
                ScreenerPresets.CONSERVATIVE,
                criteriaSummary(),
                DECISION_SUPPORT_NOTE
        );
    }

    public ConservativeEmptyStateDiagnosticResponse emptyStateDiagnostics(ScreenerRequest request) {
        return new ConservativeEmptyStateDiagnosticResponse(
                criteriaSummary(),
                List.of(
                        "Lower the margin-of-safety floor before removing quality or solvency checks.",
                        "Allow narrow moat or stable share-count results when wide-moat buyback candidates are too scarce.",
                        "Temporarily remove the dividend yield filter for durable compounders that reinvest rather than distribute cash.",
                        "Keep unavailable score or source coverage visible instead of treating missing data as a pass."
                ),
                true,
                "The current criteria are preserved. Suggested relaxations are research alternatives, not automated changes."
        );
    }

    public List<ConservativeComparisonRowResponse> agentOneComparison() {
        return List.of(
                row("BRK.B", "Berkshire Hathaway Inc.", "available", "0.0%", "78", "wide", "low leverage", "steady", "not income-focused", "available"),
                row("JNJ", "Johnson & Johnson", "available", "positive", "72", "defensive quality", "resilient", "low growth", "covered", "available"),
                row("PG", "Procter & Gamble Co.", "available", "near fair value", "70", "defensive quality", "resilient", "steady", "covered", "available"),
                row("KO", "Coca-Cola Co.", "available", "negative", "68", "brand moat", "resilient", "steady", "covered", "available"),
                row("PEP", "PepsiCo Inc.", "available", "negative", "67", "brand moat", "moderate leverage", "steady", "covered", "available"),
                row("WMT", "Walmart Inc.", "available", "thin", "65", "scale moat", "resilient", "steady", "covered", "available"),
                row("MSFT", "Microsoft Corp.", "available", "negative", "82", "wide", "net cash", "high quality", "low yield", "available"),
                row("ADP", "Automatic Data Processing Inc.", "available", "negative", "74", "service moat", "resilient", "steady", "covered", "available"),
                row("UNP", "Union Pacific Corp.", "available", "thin", "69", "network moat", "moderate leverage", "cyclical", "covered", "available"),
                row("XOM", "Exxon Mobil Corp.", "available", "cyclical", "64", "commodity-exposed", "resilient", "cyclical", "covered", "available")
        );
    }

    private List<ConservativeCriterionResponse> criteriaSummary() {
        return List.of(
                criterion("minMarginOfSafety", "Positive margin of safety", "15% minimum", "Keeps valuation discipline visible.", "Lower the floor in small steps."),
                criterion("minValueScore", "Score availability and strength", "60 minimum", "Requires a usable composite evidence base.", "Inspect score gaps before lowering the threshold."),
                criterion("minDividendYield", "Dividend signal", "1.5% minimum", "Highlights income coverage candidates for conservative review.", "Remove for reinvestment-led compounders."),
                criterion("maxDebtToEquity", "Leverage resilience", "1.0 maximum", "Flags balance sheets that may be less exposed to financing stress.", "Review sector norms before widening."),
                criterion("altmanZone", "Liquidity and solvency resilience", "SAFE", "Keeps distress signals out of the default preset.", "Allow GREY only with explicit review notes."),
                criterion("dataCoverage", "Data completeness", "Availability must be visible", "Missing provider or internal calculations must be explainable.", "Keep missing-data labels visible when filters are relaxed.")
        );
    }

    private ConservativeCriterionResponse criterion(String key, String label, String currentValue, String whyItMatters, String relaxation) {
        return new ConservativeCriterionResponse(key, label, currentValue, whyItMatters, relaxation);
    }

    private ConservativeComparisonRowResponse row(String symbol,
                                                  String companyName,
                                                  String sourceCoverage,
                                                  String marginOfSafety,
                                                  String valueScore,
                                                  String quality,
                                                  String leverageLiquidity,
                                                  String growth,
                                                  String dividend,
                                                  String dataCoverage) {
        return new ConservativeComparisonRowResponse(symbol, companyName, List.of(
                metric("valuation", "Margin of safety", marginOfSafety, "AVAILABLE", sourceCoverage),
                metric("score", "Value score", valueScore, "AVAILABLE", "Composite score available in seeded/local data."),
                metric("quality", "Quality", quality, "AVAILABLE", "Moat and business-quality signal available."),
                metric("resilience", "Leverage/liquidity", leverageLiquidity, "AVAILABLE", "Debt and solvency indicators present."),
                metric("growth", "Growth", growth, "AVAILABLE", "Recent growth trend can be reviewed."),
                metric("dividend", "Dividend indicators", dividend, "AVAILABLE", "Dividend coverage should be checked in the review packet."),
                metric("coverage", "Source/data coverage", dataCoverage, "AVAILABLE", "Availability states remain visible per L3 diagnostics.")
        ));
    }

    private ConservativeComparisonMetricResponse metric(String group, String label, String value, String availabilityStatus, String coverageNote) {
        return new ConservativeComparisonMetricResponse(group, label, value, availabilityStatus, coverageNote);
    }
}
