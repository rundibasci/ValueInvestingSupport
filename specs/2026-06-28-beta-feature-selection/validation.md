# HD4 - Beta Feature Selection Validation

## Merge Readiness Standard

HD4 can be merged when the selected trust-blocker features are implemented, documented, and validated without expanding into unrelated platform redesign.

## Traceability Checklist

- Score availability transparency: implemented in selected API/UI surfaces or explicitly scoped with remaining gaps documented.
- Data-quality classification: structured states distinguish available, stale, pending, provider-limited, missing seeded history, missing internal computation, and guardrail-blocked where supported by existing data.
- Portfolio concentration warnings: portfolio detail and add-to-portfolio flows show holding/sector exposure or explain why exposure cannot be calculated.
- Watchlist research rationale: watchlist items support user-owned note and monitoring reason/category.
- Screener empty-state diagnostics: deferred with rationale and named follow-up.
- Cross-symbol comparison: deferred with rationale and named follow-up.
- Story-versus-fundamentals review support: deferred with rationale and named follow-up.
- Persona replay scripts: deferred to Group I regression/demo scripting, with HD4 smoke validation for impacted workflows.

## Automated Validation

Backend:

- Run targeted unit and integration tests for changed DTOs, mappers, services, repositories, and controllers.
- Cover score/data-quality states that can be produced deterministically from fixtures or mocked services.
- Cover portfolio concentration thresholds and missing price/sector data.
- Cover watchlist rationale create, update, read, validation, and ownership behavior.

Frontend:

- Run TypeScript checks and production build.
- Run targeted component or route tests where available.
- Verify status labels, warning states, and rationale fields render without layout breakage on desktop and mobile breakpoints.

Evidence captured on 2026-06-28:

- Backend targeted tests passed:
  - `./mvnw.cmd test "-Dtest=PortfolioControllerTest,WatchlistControllerTest,ScoreControllerTest,ScreenerControllerTest,SecurityReviewControllerTest,SecurityReviewServiceTest"`
  - Result: 38 tests run, 0 failures, 0 errors.
- Backend targeted test selector rerun after frontend changes:
  - `./mvnw.cmd test "-Dtest=PortfolioControllerTest,WatchlistControllerTest,WatchlistIT,ScoreControllerTest,ScreenerControllerTest,SecurityReviewControllerTest,SecurityReviewServiceTest"`
  - Result: 38 tests run, 0 failures, 0 errors. Note: `WatchlistIT` is integration-style and was not picked up by Surefire in this selector.
- Frontend production build passed:
  - `npm run build`
  - Result: TypeScript build and Vite production build completed successfully.

Repository hygiene:

- Run `git diff --check`.
- Confirm no secrets or local runtime logs are added.
- Confirm unrelated untracked runtime logs remain uncommitted.

Repository hygiene evidence:

- `git diff --check`: passed; Git reported line-ending normalization warnings only.
- Existing untracked runtime logs remain unmodified and uncommitted.

## Local Demo Smoke Evidence

Run the local full demo after implementation and verify:

1. Review or score visibility flow
   - Seed or open a symbol with complete data.
   - Confirm score/valuation availability is visible.
   - Open or simulate a symbol with incomplete data.
   - Confirm the UI explains the missing/stale/blocked state.

2. Portfolio concentration flow
   - Create or open a portfolio with a dominant holding or sector.
   - Confirm concentration warning appears with factual decision-support copy.
   - Test a diversified or incomplete-data case and confirm warning behavior is appropriate.

3. Watchlist rationale flow
   - Add a symbol to a watchlist with a note and monitoring reason such as `WAIT_FOR_BETTER_PRICE`.
   - Reload or revisit the watchlist.
   - Confirm rationale persists and remains editable by the owning user only.

Status on 2026-06-28:

- Local full-demo smoke replay was not run in this implementation turn.
- Required before merge if the branch is promoted as demo-validated: review/score visibility, portfolio concentration, and watchlist rationale flows should be exercised against localstack/demo data.

## Acceptance Criteria

- The HD4 selected feature set is traceable to HD3 beta findings.
- Every HD3 extracted requirement is classified as implemented, deferred, or rejected with rationale.
- Selected features include both backend/API and frontend/UI changes where needed.
- Validation includes targeted automated tests plus local demo smoke evidence.
- MiFID II decision-support boundary is preserved.
- No selected feature silently expands into unrelated navigation, visual redesign, identity, cloud, or observability work.
