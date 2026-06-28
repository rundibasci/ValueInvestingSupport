# Plan - Phase I1: Test Coverage

1. Baseline Coverage Inventory
   - Review current backend and frontend test suites.
   - Map existing coverage against the I1 roadmap list: calculators, screener API, valuation endpoint, auth flow, HD4 workflows, data-quality states, and persona replay.
   - Identify tests that are flaky, live-provider-dependent, or blocked by missing deterministic fixtures.
   - Produce a short gap list in the implementation notes or validation evidence.

2. Calculator And Domain Unit Tests
   - Add or complete unit tests for DCF, Graham, DDM, Margin of Safety, and Value Score calculations.
   - Cover normal cases, conservative defaults, invalid inputs, guardrail cases, rounding, null/missing optional values, and eligibility rules.
   - Add tests for availability/status mapping logic used by score, valuation, review, screener, portfolio, or watchlist flows.

3. Auth And Authorization Integration Tests
   - Cover successful username/password login using deterministic test users.
   - Verify protected API access with a valid JWT and rejection without one.
   - Verify role restrictions for admin-only endpoints used by seed, cache, jobs, or pipeline/demo flows where present.
   - Cover refresh/logout/revocation behavior where the current auth implementation supports reliable local validation.

4. Screener, Valuation, And Research API Integration Tests
   - Add seeded-data tests for screener success, filtering, sorting, pagination, presets/sectors/exchanges where implemented, and empty-result diagnostics if HD4 implemented them.
   - Add valuation endpoint tests for successful DCF/custom valuation, RULE-06 guardrail behavior, stale or missing snapshot behavior, and MiFID II disclaimer presence in fair-value responses.
   - Cover security/review endpoint states that expose availability labels, source/freshness, and missing-data reasons.

5. HD4 Beta-Driven Workflow Tests
   - Test score/data-quality states selected in HD4, including available, stale, pending/provider-limited, missing seeded history, missing internal computation, and guardrail-blocked where deterministic examples can be created.
   - Test concentration warning thresholds for holdings and sectors using seeded portfolio data.
   - Test watchlist rationale persistence after reload or refetch.
   - Test conservative workflow diagnostics or comparison behavior where implemented by HD4.

6. Persona Replay Pack
   - Create deterministic replay scripts or tests for the three HD3 personas where practical.
   - Prioritize Agent 1 prudent-value replay with the 10-symbol set: `BRK.B`, `JNJ`, `PG`, `KO`, `PEP`, `WMT`, `MSFT`, `ADP`, `UNP`, `XOM`.
   - Seed local fixture data, run the relevant research/review/portfolio/watchlist checks, and capture structured output without describing the model as investable.
   - Mark any persona step that cannot yet be automated with a concrete reason and a follow-up phase.

7. Frontend Workflow Tests
   - Add or complete React tests for user-visible states affected by I1: auth guard behavior, screener/review availability labels, valuation disclaimer rendering, concentration warnings, and watchlist rationale UI.
   - Prefer component or route-level tests with mocked API responses over end-to-end tests that require live services.
   - Keep visual assertions focused on behavior and accessible text/states.

8. Reliability And CI Cleanup
   - Remove or quarantine live-provider assumptions from required test paths.
   - Ensure required commands run in a clean local environment with documented setup.
   - Update any test fixture documentation needed for future contributors.
   - Run the reliable validation set and record command results in `validation.md`.

9. Merge Readiness Review
   - Confirm I1 roadmap requirements are implemented, deferred with rationale, or linked to I2/future phases.
   - Confirm secrets and generated logs are not committed.
   - Confirm new tests are deterministic and scoped to platform behavior.
   - Prepare a concise handoff summary for the implementation merge.
