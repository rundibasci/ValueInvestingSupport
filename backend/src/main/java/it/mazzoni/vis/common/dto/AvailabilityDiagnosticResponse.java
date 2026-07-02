package it.mazzoni.vis.common.dto;

import it.mazzoni.vis.common.AvailabilityStatus;

import java.util.List;

public record AvailabilityDiagnosticResponse(
        AvailabilityStatus status,
        String exampleCategory,
        String exampleReason,
        List<String> surfaces,
        String conservativeInterpretation,
        String decisionSupportNote
) {
}
