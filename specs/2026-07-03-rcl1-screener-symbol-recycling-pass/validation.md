# Validation - Phase RCL1: Screener And Symbol Recycling Pass

## Acceptance Checks

- `POST /api/v1/screener` with an empty JSON object no longer returns `500`.
- UI-standard screener requests with percentage thresholds still work.
- Fractional or malformed threshold payloads return either normalized results or `400` validation errors with clear field messages.
- Screener empty state no longer says `companyies`.
- Screener empty state can coexist with an Agent 1 comparison table without contradictory wording.
- The screener route exposes one primary `main` landmark.
- `BRK.B` and `BRK-B` can both resolve through security/review lookup paths.
- Portfolio enrichment does not fail solely because a holding uses `BRK.B` while seeded securities use `BRK-B`.
- Validation evidence includes relevant route/payload/status/log-correlation notes.

## Validation Commands

Run from the repository root unless noted:

```powershell
cd backend; .\mvnw.cmd test '-Dtest=ScreenerServiceTest,ScreenerControllerTest'
cd frontend; npm run typecheck
cd frontend; npm run build
```

If focused test names differ after inspection, record the exact commands run here before merge.

## Validation Results

- `cd backend; .\mvnw.cmd test '-Dtest=ScreenerServiceTest,ScreenerControllerTest'` - passed, 14 tests.
- `cd frontend; npm run typecheck` - passed.
- `cd frontend; npm run build` - passed; Vite reported only the existing large chunk warning.

## Manual QA

With the local demo stack running:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/screener -ContentType application/json -Body '{}'
```

Then verify:

- `/screener` loads without contradictory empty-state copy.
- `/screener` has one primary `main` landmark.
- `/securities/BRK.B/review` and `/securities/BRK-B/review` resolve consistently or redirect/alias consistently.
- Portfolio holdings using either Berkshire class B symbol show price/data status consistently.

## Merge Readiness

- Spec files are present and non-empty.
- Focused backend tests pass.
- Frontend validation passes for touched files.
- Docker/backend logs show no unexpected `5xx` for fixed screener payloads during validation.
- Worktree staging excludes unrelated beta screenshots, generated logs, and replay artifacts unless intentionally documented.

## Known Risks

- Provider conventions may differ between FMP and Yahoo Finance for Berkshire class B. Alias handling must remain explicit and test-covered.
- Existing demo data may already contain both symbol forms; migration or alias behavior must avoid destructive cleanup.
- Empty screener behavior may depend on seeded universe size and role/session state.
