# VM2 - Valuation Transparency Frontend Validation

## Acceptance Checks

- Review API exposes VM1 transparency fields without changing valuation formulas.
- Review page displays WACC and each available WACC input with source/fallback metadata.
- Review page displays DCF terminal-value percentage and warns when terminal dependence is above 70%.
- Review page displays EPV as a conservative zero-growth floor and owner earnings with maintenance-capex assumption.
- Review page displays Graham checklist criteria with pass/fail/no-data status and a summary count.
- Review page shows a DCF sensitivity matrix with color-coded margin-of-safety cells and base-case emphasis.
- Composite-weight controls allow local DCF/Graham/DDM/EPV comparison and keep weights totaling 100.
- Missing VM1 data renders as unavailable state, not a crash.

## Test Strategy

- Run `cd backend; .\mvnw.cmd test`.
- Run `cd frontend; npm run typecheck`.
- Run `cd frontend; npm run build`.

## Manual QA

- Inspect `git diff --stat`.
- Confirm spec files are non-empty.
- Confirm untracked runtime logs remain uncommitted.

## Merge Readiness

- Backend tests pass.
- Frontend typecheck and build pass.
- Branch contains only VM2 spec, API exposure, frontend UI, tests, and changelog updates required for merge.
- No secrets, `.env`, provider keys, or runtime logs are committed.

## Known Risks

- Derived sensitivity data uses conservative default growth assumptions because VM1 does not persist the original DCF parameter snapshot.
- Composite-weight controls are local-only until user preference persistence is exposed by a later phase.
