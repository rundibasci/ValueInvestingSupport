# Validation - Phase JC2: Job Scheduling Runtime Control

## Acceptance Checks

- `GET /api/v1/admin/jobs` includes persisted enabled state and effective cron expression.
- `PUT /api/v1/admin/jobs/{jobName}/enabled` persists and returns the updated state.
- `PUT /api/v1/admin/jobs/{jobName}/cron` rejects invalid cron strings and persists valid overrides.
- `POST /api/v1/admin/jobs/{jobName}/run` accepts optional `symbols`, `exchange`, and `dataTypes` fields and returns a `jobRunId`.
- `GET /api/v1/admin/jobs/runs/{jobRunId}/status` returns status, processed count, elapsed time, and error count for known runs and 404 for unknown runs.
- Demo pages expose the new controls without adding external dependencies.

## Test Strategy

- Run targeted backend tests:

```powershell
.\mvnw.cmd test -Dtest=JobAdminServiceTest,JobAdminControllerTest
```

- If targeted tests reveal affected shared behavior, run the full backend test suite:

```powershell
.\mvnw.cmd test
```

## Manual QA

- Start the backend with a local profile and authenticate as admin.
- Call the enabled and cron endpoints with valid and invalid payloads.
- Trigger a scoped job run and poll its status.
- Open `feature-demo.html` or `full-demo.html` and confirm the job-control panel can call the new endpoints.

## Merge Readiness

- Spec files are present and non-empty.
- Backend targeted tests pass.
- Any limitations around dynamic rescheduling or scope enforcement are documented in this spec before merge.

## Known Risks

- Implemented limitation: existing scheduled jobs use annotation-based cron configuration. JC2 persists and exposes cron overrides through the admin API, but live rescheduling is deferred until a scheduler registrar or Cloud Run Job scheduler phase.
- Implemented limitation: existing job implementations do not all support scoped execution by symbol, exchange, and data type. JC2 accepts and records scope metadata on the manual run so status/history are traceable; per-job scope enforcement remains a follow-up.
