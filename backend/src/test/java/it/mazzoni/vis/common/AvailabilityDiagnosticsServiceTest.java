package it.mazzoni.vis.common;

import it.mazzoni.vis.common.dto.AvailabilityDiagnosticResponse;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityDiagnosticsServiceTest {

    private final AvailabilityDiagnosticsService service = new AvailabilityDiagnosticsService();

    @Test
    void all_returnsOneDeterministicExampleForEverySharedStatus() {
        var diagnostics = service.all();

        assertThat(diagnostics).hasSize(AvailabilityStatus.values().length);
        assertThat(diagnostics.stream().map(AvailabilityDiagnosticResponse::status).collect(Collectors.toSet()))
                .isEqualTo(EnumSet.allOf(AvailabilityStatus.class));
        assertThat(diagnostics)
                .allSatisfy(item -> {
                    assertThat(item.exampleCategory()).isNotBlank();
                    assertThat(item.exampleReason()).isNotBlank();
                    assertThat(item.surfaces()).isNotEmpty();
                    assertThat(item.conservativeInterpretation()).isNotBlank();
                    assertThat(item.decisionSupportNote()).isNotBlank();
                    assertThat(item.decisionSupportNote()).doesNotContainIgnoringCase("buy");
                    assertThat(item.decisionSupportNote()).doesNotContainIgnoringCase("sell");
                });
    }
}
