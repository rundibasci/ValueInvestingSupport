package it.mazzoni.vis.screener;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConservativeWorkflowServiceTest {

    private final ConservativeWorkflowService service = new ConservativeWorkflowService();

    @Test
    void preset_containsRequiredConservativeCriteria() {
        var preset = service.preset();
        var keys = preset.criteriaSummary().stream()
                .map(item -> item.key())
                .collect(Collectors.toSet());

        assertThat(preset.criteria().minMarginOfSafety()).isNotNull();
        assertThat(preset.criteria().minValueScore()).isNotNull();
        assertThat(preset.criteria().minDividendYield()).isNotNull();
        assertThat(preset.criteria().maxDebtToEquity()).isNotNull();
        assertThat(keys).containsAll(Set.of(
                "minMarginOfSafety",
                "minValueScore",
                "minDividendYield",
                "maxDebtToEquity",
                "altmanZone",
                "dataCoverage"
        ));
        assertThat(preset.decisionSupportNote()).doesNotContainIgnoringCase("buy");
        assertThat(preset.decisionSupportNote()).doesNotContainIgnoringCase("sell");
    }

    @Test
    void emptyStateDiagnostics_preserveCriteriaAndSuggestRelaxations() {
        var diagnostics = service.emptyStateDiagnostics(ScreenerPresets.CONSERVATIVE);

        assertThat(diagnostics.currentCriteriaPreserved()).isTrue();
        assertThat(diagnostics.likelyEliminators()).hasSizeGreaterThanOrEqualTo(5);
        assertThat(diagnostics.suggestedRelaxations())
                .anySatisfy(item -> assertThat(item).containsIgnoringCase("margin-of-safety"))
                .anySatisfy(item -> assertThat(item).containsIgnoringCase("dividend"));
        assertThat(diagnostics.decisionSupportNote()).containsIgnoringCase("preserved");
    }

    @Test
    void agentOneComparison_containsRequiredMetricGroupsForEverySymbol() {
        var rows = service.agentOneComparison();

        assertThat(rows).hasSize(10);
        assertThat(rows).allSatisfy(row -> {
            var groups = row.metrics().stream()
                    .map(metric -> metric.group())
                    .collect(Collectors.toSet());
            assertThat(groups).containsExactlyInAnyOrder(
                    "valuation",
                    "score",
                    "quality",
                    "resilience",
                    "growth",
                    "dividend",
                    "coverage"
            );
        });
    }
}
