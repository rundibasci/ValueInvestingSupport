package it.mazzoni.vis.common.dto;

import it.mazzoni.vis.common.AvailabilityStatus;

import java.time.LocalDate;

public record AvailabilityResponse(
        AvailabilityStatus status,
        String reason,
        LocalDate dataAsOf
) {
    public static AvailabilityResponse available(LocalDate dataAsOf) {
        return new AvailabilityResponse(AvailabilityStatus.AVAILABLE, "Data is available.", dataAsOf);
    }

    public static AvailabilityResponse missingComputation(String reason) {
        return new AvailabilityResponse(AvailabilityStatus.MISSING_INTERNAL_COMPUTATION, reason, null);
    }

    public static AvailabilityResponse providerLimited(String reason) {
        return new AvailabilityResponse(AvailabilityStatus.PROVIDER_LIMITED, reason, null);
    }
}
