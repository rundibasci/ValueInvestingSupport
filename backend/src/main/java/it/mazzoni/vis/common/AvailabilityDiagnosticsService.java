package it.mazzoni.vis.common;

import it.mazzoni.vis.common.dto.AvailabilityDiagnosticResponse;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AvailabilityDiagnosticsService {

    private final Map<AvailabilityStatus, AvailabilityDiagnosticResponse> diagnostics;

    public AvailabilityDiagnosticsService() {
        diagnostics = new EnumMap<>(AvailabilityStatus.class);
        add(AvailabilityStatus.AVAILABLE, "Score",
                "A current value score exists with a stored calculation date.",
                List.of("security review", "screener", "portfolio holdings", "watchlist review links"),
                "Use the metric as available evidence, then inspect the underlying inputs before relying on it.",
                "Available data is still model input, not personalised investment advice.");
        add(AvailabilityStatus.STALE, "Fundamentals",
                "The latest local financial snapshot is older than the freshness guard.",
                List.of("security review", "screener", "portfolio holdings"),
                "Refresh or reseed the symbol before treating valuation or score output as current.",
                "A stale label explains recency risk; it does not say whether the stock should be bought or sold.");
        add(AvailabilityStatus.PENDING, "Ingestion",
                "A seed or recomputation job has been requested but has not produced final local data yet.",
                List.of("seed results", "security review", "watchlist review links"),
                "Wait for the job to finish or check job diagnostics before interpreting missing values.",
                "Pending work means evidence is incomplete.");
        add(AvailabilityStatus.PROVIDER_LIMITED, "Dividends",
                "The current provider or plan did not return the requested data category.",
                List.of("security review", "screener", "portfolio holdings"),
                "Treat the missing category as a coverage gap and verify it from primary filings or another provider.",
                "Provider limitation is a data-source constraint, not a recommendation.");
        add(AvailabilityStatus.MISSING_SEEDED_HISTORY, "Historical fundamentals",
                "The symbol exists locally, but the required historical records have not been seeded.",
                List.of("security review", "screener", "portfolio holdings", "watchlist review links"),
                "Seed more history before using trend, guardrail, or long-horizon quality conclusions.",
                "Missing history reduces confidence in analysis.");
        add(AvailabilityStatus.MISSING_INTERNAL_COMPUTATION, "Value score",
                "Raw input data exists, but the platform has not computed or persisted this derived metric.",
                List.of("security review", "screener", "portfolio holdings"),
                "Run or rerun the relevant valuation, score, or risk computation before comparing symbols.",
                "A missing computation should be fixed or disclosed before decision support is complete.");
        add(AvailabilityStatus.GUARDRAIL_BLOCKED, "DCF valuation",
                "A valuation model was skipped because eligibility rules such as positive free-cash-flow history were not met.",
                List.of("security review", "screener", "portfolio holdings"),
                "Inspect the guardrail reason and use only the remaining eligible model outputs.",
                "Guardrails prevent weak model output from looking precise.");
    }

    public List<AvailabilityDiagnosticResponse> all() {
        return List.copyOf(diagnostics.values());
    }

    private void add(AvailabilityStatus status, String exampleCategory, String exampleReason,
                     List<String> surfaces, String conservativeInterpretation, String decisionSupportNote) {
        diagnostics.put(status, new AvailabilityDiagnosticResponse(
                status,
                exampleCategory,
                exampleReason,
                surfaces,
                conservativeInterpretation,
                decisionSupportNote
        ));
    }
}
