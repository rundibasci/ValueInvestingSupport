# Validation - Phase I1: Test Coverage

## Merge Criteria

Phase I1 is mergeable when all required checks below pass reliably without paid/live provider dependencies.

1. Backend tests pass with deterministic local configuration.
   - Run the backend unit and integration test suite using the existing Maven command for this repository.
   - Tests must not require a real FMP key, Yahoo Finance availability, SMTP delivery, Google OAuth, or cloud infrastructure.

2. Frontend tests and build pass.
   - Run the frontend test command where present.
   - Run the TypeScript/Vite build command where present.
   - Mock API responses for workflow states rather than relying on a live backend unless the repository already has a stable local test harness.

3. Calculator and domain coverage is complete for I1.
   - DCF, Graham, DDM, Margin of Safety, and Value Score have normal, boundary, and guardrail coverage.
   - Availability/status mapping has deterministic tests for implemented states.

4. Auth and API integration coverage is complete for I1.
   - Auth success/failure and protected-route behavior are tested.
   - Screener and valuation endpoints are tested for success and relevant missing/stale/guardrail states.
   - Admin-only behavior is tested where I1 touches seed, pipeline, job, or cache workflows.

5. HD4 beta-driven workflows are covered or explicitly deferred.
   - Score/data-quality states have deterministic tests or a documented reason for any missing state.
   - Concentration warning thresholds are covered.
   - Watchlist rationale persistence is covered.
   - Conservative workflow diagnostics or comparison features selected in HD4 are covered where implemented.

6. Persona replay is practical and documented.
   - At least one deterministic replay script or test covers the Agent 1 prudent-value workflow path where fixtures permit it.
   - The remaining HD3 persona workflows are either automated/scripted or deferred with concrete blockers.
   - Replay outputs preserve the decision-support boundary and avoid investable-model language.

7. Repository hygiene is clean.
   - No secrets are committed.
   - Existing unrelated logs remain untracked or ignored and are not included in the phase.
   - Test fixtures are small, deterministic, and clearly named.

## Evidence To Record During Implementation

| Check | Command or Evidence | Result |
|---|---|---|
| Backend tests | `cd backend; mvn -q test` | Passed on 2026-06-28 |
| Focused backend tests | `cd backend; mvn -q "-Dtest=AvailabilityResponseTest,ScoreControllerTest,SecurityReviewServiceTest,WatchlistControllerTest,PortfolioControllerTest" test` | Passed on 2026-06-28 |
| Integration assertions touched by I1 | `cd backend; mvn -q -Pintegration-test "-Dtest=PortfolioIT,WatchlistIT" test` | Passed on 2026-06-28 with Testcontainers PostgreSQL |
| Frontend tests | `package.json` has no frontend test script configured | Not applicable for this phase |
| Frontend build | `cd frontend; npm run build` | Passed on 2026-06-28; Vite reported the existing large chunk warning |
| Persona replay | `scripts/i1-persona-replay.ps1` and `persona-replay.md` | Added deterministic local-backend replay for Agent 1 prudent-value workflow |
| Coverage/deferred matrix | This validation file plus `persona-replay.md` | Agent 1 scripted; allocator and journalist persona automation deferred until deterministic fixture coverage exists |

## Known Validation Boundaries

- Live FMP/Yahoo integration is optional and must not be required for merge.
- Full browser walkthrough evidence is useful but not mandatory for I1 unless implementation changes frontend behavior beyond tests.
- I2 observability validation starts in the next roadmap phase and should not block I1 unless tests expose a correctness issue.
