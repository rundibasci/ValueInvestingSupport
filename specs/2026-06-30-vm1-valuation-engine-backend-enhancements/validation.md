# VM1 - Valuation Engine Backend Enhancements Validation

## Acceptance Checks

- WACC result exposes inputs, source/fallback metadata, computed WACC, and persists with a valuation result.
- DCF result includes terminal value percentage and `highTerminalDependence`.
- Sensitivity service returns a WACC by terminal-growth matrix with at least 9 cells.
- EPV returns a zero-growth per-share floor and skips when fewer than 5 annual earnings records exist.
- Owner earnings uses reported values plus configurable maintenance-capex approximation.
- Graham checklist returns each criterion as `PASS`, `FAIL`, or `INSUFFICIENT_DATA` with actual values when available.
- Composite weights validate sum-to-100 inputs and reduce DCF weight when terminal dependence is high.
- Flyway migration adds VM1 persistence without deleting existing data.

## Test Strategy

- Run `cd backend; .\mvnw.cmd test`.
- Unit tests cover deterministic calculator behavior and service-level edge cases.
- Existing valuation tests continue to pass.

## Manual QA

- Review `git diff --stat`.
- Confirm spec files are non-empty.
- Confirm untracked runtime logs remain untouched.

## Merge Readiness

- Maven tests pass.
- Branch contains only VM1 spec, backend implementation, tests, migration, and changelog updates required by merge.
- No secrets or environment-specific files are committed.

## Known Risks

- Provider-specific beta/rate data is not yet modeled consistently; this phase uses explicit fallback metadata rather than hiding the gap.
- User preference APIs are intentionally not exposed until the frontend phase needs them.
