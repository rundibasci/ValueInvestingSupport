# SC2 Universe Curation UI & Workflow Validation

## Acceptance Checks

- An admin can open the Universe Curation page from the authenticated app.
- Template selection pre-fills exchange, sector, market-cap, volume, max-symbol, and sort criteria when present in the template.
- Preview sends criteria to the SC1 preview endpoint and renders total matches, capped status, warnings, and preview rows before seeding.
- Seed action sends the current criteria to the SC1 seed endpoint and renders per-symbol status rows.
- Large-universe safeguards are visible through max-symbol controls and capped warnings.
- Exclusion controls are not presented as functional unless a backend contract exists.

## Test Strategy

- Typecheck the React code with strict TypeScript.
- Build the Vite frontend to verify production bundling and CSS generation.
- Use existing backend SC1 tests if contract changes become necessary.

## Validation Commands

- `npm run typecheck` from `frontend` - passed on 2026-07-02.
- `npm run build` from `frontend` - passed on 2026-07-02; Vite reported the pre-existing large chunk warning for the application bundle.
- Optional if backend contract changes: `backend/mvnw.cmd test "-Dtest=UniverseSelectionServiceTest,UniverseSelectionControllerTest,UniverseSeedControllerTest"`.

## Manual QA

- Load the page at desktop and mobile widths.
- Verify controls remain scannable and do not overlap.
- Verify preview and seed error states remain visible without losing entered criteria.
- Vite dev server was started at `http://127.0.0.1:5173` and returned HTTP 200. In-app browser automation could not be completed because the session exposed the reset handle but not the JavaScript execution handle required by the browser plugin.

## Merge Readiness

- Spec files, frontend implementation, and directly relevant docs/changelog updates only.
- Existing untracked runtime log files remain uncommitted.
- Validation commands pass before vault logging and merge.

## Known Risks

- Without a dedicated active-universe summary endpoint, the summary can only reflect the latest preview/seed interaction and not the full persisted universe distribution.
- Backend exclusion persistence appears deferred unless an endpoint already exists; the UI must not imply exclusions are saved when they are not.
