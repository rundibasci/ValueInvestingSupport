# PW1 Professional Workflow Backend Validation

## Acceptance Checks

- Portfolio/watchlist add and remove actions create immutable research snapshots.
- A user can list their own decision snapshots; admin access can list broader results when existing role checks support it.
- Checklist CRUD works for authenticated users and does not expose another user's checklist.
- Checklist evaluation persists a result and returns per-criterion `PASS`, `FAIL`, `NO_DATA`, or `MANUAL_REQUIRED`.
- Confidence scoring returns an overall high/medium/low level plus factor explanations.
- Data verification returns structured flags without throwing when provider-backed fields are missing.
- Competence preferences can be read and updated for the authenticated user.
- Advisor acknowledgement state can be read and updated without presenting the platform as regulated advice.

## Test Strategy

- Add unit tests for pure services where possible.
- Add Spring MVC or repository tests only where controller/security integration needs coverage.
- Prefer deterministic fixtures over live FMP/Yahoo calls.

## Commands

- `cd backend; mvn test`
- If full tests are too slow or blocked by local services, run the narrowest affected `mvn -Dtest=... test` command and document the limitation.

## Manual QA

- Inspect generated migration for compatibility with existing Flyway naming and SQL style.
- Confirm no committed secrets or provider credentials.
- Confirm audit entities do not expose update/delete operations.

## Merge Readiness

- Spec files exist and are non-empty.
- Implementation and tests are committed on `phase/pw1-professional-workflow-backend`.
- Backend validation command passes.
- Obsidian activity note is updated before merge.

## Known Risks

- Existing portfolio/watchlist service boundaries may require a narrower audit hook than the roadmap describes.
- Some confidence or verification factors may need nullable/no-data outputs until later data coverage phases fill gaps.
