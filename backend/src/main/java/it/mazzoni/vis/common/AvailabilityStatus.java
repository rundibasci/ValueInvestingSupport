package it.mazzoni.vis.common;

public enum AvailabilityStatus {
    AVAILABLE,
    STALE,
    PENDING,
    PROVIDER_LIMITED,
    MISSING_SEEDED_HISTORY,
    MISSING_INTERNAL_COMPUTATION,
    GUARDRAIL_BLOCKED
}
