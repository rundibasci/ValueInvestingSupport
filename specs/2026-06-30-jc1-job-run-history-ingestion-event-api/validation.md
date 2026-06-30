# JC1 Validation

## Acceptance Checks

- `GET /api/v1/admin/jobs` returns all registered jobs with enabled status, cron expression when known, and latest run summary.
- `GET /api/v1/admin/jobs/{jobName}/history` returns paginated `JobRunLog` history for that job.
- `GET /api/v1/admin/jobs/{jobName}/events` returns paginated per-symbol ingestion events and supports `runId`, `symbol`, and `status` filters.
- `IngestionEvent` rows are persisted for instrumented symbol-level ingestion work with data type, status, source, and error detail where available.
- Existing manual job trigger behavior is unchanged.
- Non-admin users cannot access the new admin endpoints.

## Test Strategy

- Backend unit/slice tests for service mapping and controller responses.
- Repository tests or integration tests for filtered ingestion-event queries.
- Existing backend test suite to guard against job, security, and migration regressions.

## Commands

```powershell
.\mvnw test
```

Run from `backend/`.

## Manual QA

- Start the backend with the local or demo profile.
- Trigger a seed or ingestion job.
- Request the jobs list, history, and event endpoints as ADMIN.
- Confirm event rows line up with the triggered symbols and show failures without hiding successful symbols.

## Merge Readiness

- Spec files exist and match implemented behavior.
- Backend tests pass.
- Changelog updated by merge workflow.
- Obsidian activity log records implementation summary and validation results.

## Known Risks

- Some existing jobs may not expose symbol-level boundaries; this phase may only instrument paths where symbol/data-type context is already available.
- If run logs do not store cron or enabled state, the API may derive those values from in-code registration metadata until JC2 adds runtime controls.
