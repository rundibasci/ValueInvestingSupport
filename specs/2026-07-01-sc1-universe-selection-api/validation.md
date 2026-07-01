# SC1 Universe Selection Criteria & Filtering API Validation

## Acceptance Checks

- `GET /api/v1/admin/universe/templates` returns `us-blue-chip`, `dividend-aristocrats`, `value-candidates`, and `defensive-quality`.
- `POST /api/v1/admin/universe/preview` filters stock-list rows by exchange, country, sector, market cap, and volume before seeding.
- Preview responses include total matches, returned symbols, capped status, and a clear cap warning when applicable.
- `POST /api/v1/admin/universe/seed` seeds the previewed criteria result through the existing seed pipeline.
- Existing `/api/v1/universe/seed?tickers=` remains compatible.

## Test Strategy

- Focused service tests for deterministic filtering and capping.
- MockMvc tests for admin template, preview, and seed endpoints.
- Existing seed controller tests remain passing.

## Validation Commands

- `backend/mvnw.cmd test "-Dtest=UniverseSelectionServiceTest,UniverseSelectionControllerTest,UniverseSeedControllerTest"` — passed on 2026-07-01 with 8 tests, 0 failures.
- `backend/mvnw.cmd test` — passed on 2026-07-01 with 338 tests, 0 failures.

## Merge Readiness

- Worktree contains only SC1 spec files, backend implementation, focused tests, changelog update, and external vault activity note if applicable.
- Runtime log files present before the phase remain untracked and are not committed.

## Known Risks

- Provider stock-list metadata completeness varies; missing optional metadata can reduce matches for criteria requiring those fields.
- Large criteria-based seed requests remain synchronous in this phase and should use `maxSymbols` conservatively.
