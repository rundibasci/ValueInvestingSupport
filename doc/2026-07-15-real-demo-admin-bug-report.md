# Real Demo ADMIN Walkthrough — Bug Report

Date: 2026-07-15  
Environment: clean `realDemo`, ADMIN profile

## BUG-1: SJM growth and company review return HTTP 500

Severity: High for SJM analysis; Medium platform-wide  
Status: Fixed in working tree

### Steps to reproduce

1. Authenticate as `admin@realdemo.local`.
2. Request `GET /api/v1/securities/SJM/growth`.
3. Request `GET /api/v1/securities/SJM/review`.

### Actual result

Both calls return HTTP 500 with the generic Spring error payload. The frontend growth view and aggregate review cannot be completed for SJM.

### Expected result

The API should return a valid growth response. If CAGR is mathematically unavailable for a series (for example because a base value is negative), the affected metric should be represented as unavailable with an explanatory availability message; the entire security review must not fail.

### Technical evidence

Backend root cause:

```text
java.lang.NumberFormatException: Character N is neither a decimal digit number,
decimal point, nor "e" notation exponential mark.
at java.math.BigDecimal.valueOf(...)
at it.mazzoni.vis.security.GrowthService.cagr(GrowthService.java:46)
at it.mazzoni.vis.security.GrowthService.metrics(GrowthService.java:29)
at it.mazzoni.vis.security.GrowthService.compute(GrowthService.java:21)
```

This is consistent with a non-finite floating-point result (`NaN`) being passed to `BigDecimal.valueOf`. The aggregate review also invokes the growth service, so the same defect propagates to `/review`.

### Scope evidence

A complete 64-symbol sweep was performed. SJM was the only persisted symbol with HTTP 500 among the 13 authoritative value-analysis sources. Its other 11 sources returned HTTP 200. APD, BROWN and WBA returned expected unavailable/404 results because their seed failed; those are tracked as provider/data limitations, not this application defect.

### Suggested acceptance criteria

- CAGR calculation detects zero, negative-base, non-finite, or otherwise unsupported inputs before conversion to `BigDecimal`.
- `/api/v1/securities/SJM/growth` returns 200 with explicit unavailable metrics where needed.
- `/api/v1/securities/SJM/review` returns 200 and carries the growth availability state.
- Add a regression test using SJM-like negative-to-positive or otherwise non-finite CAGR input.

### Resolution

`GrowthService.cagr` now treats a CAGR as unavailable when either endpoint is non-positive. It also validates that both the computed ratio and final CAGR are finite before converting the result to `BigDecimal`. Unsupported series therefore produce a `null` metric instead of propagating an HTTP 500.

Regression coverage was added for:

- A positive historical value followed by a negative latest value, reproducing the SJM failure mode.
- An extreme numeric input whose `double` ratio becomes non-finite.

In both cases `/growth` returns HTTP 200, preserves valid growth metrics, and represents only the unsupported metric as unavailable.

### Verification

- `GrowthControllerTest`: PASS on Java 21, including both new regression cases.
- `SecurityReviewServiceTest`: PASS on Java 21, confirming compatibility of the aggregate review path.
- Full backend suite: 378 tests executed; the growth/review fix passed. Two unrelated existing failures remain in `UniverseSelectionServiceTest` and `FmpWithYahooFallbackMarketDataClientTest` and are outside this bug's scope.
