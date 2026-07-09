# Validation - Phase JM: Scheduled Job Monitor Console

## Automated Checks

- `cd backend && mvn -Dtest=JobAdminServiceTest test`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`

## Acceptance Checks

- `GET /api/v1/admin/jobs/monitor` returns one row for every registered job with effective cron, enabled state, last run, running run if present, and next-run metadata when parseable.
- `POST /api/v1/admin/jobs/{jobName}/run` returns `202` and a `jobRunId` when no run is active.
- A second manual launch for the same running job returns `409` with the active `jobRunId`.
- The React `/admin/jobs` page is visible only to ADMIN users.
- The monitor table supports run-now launch, scope inputs, enable/disable, history inspection, event inspection, status filtering, and visible run progress.
- Error and skipped states show actionable text without exposing secrets.

## Manual QA

- Log in as ADMIN and open `/admin/jobs`.
- Confirm the table loads and empty-history jobs show a calm unavailable state.
- Launch a harmless or locally stubbed job and watch the progress panel update until completion.
- Disable and re-enable a job, confirming the table updates.
- Open history and events for a selected row.
- Log in or simulate as INVESTOR and verify admin navigation does not show Jobs and direct route access redirects away.

## Risks

- Dynamic next-run calculation is informational only; fixed `@Scheduled` annotations still govern actual scheduler timing until a later scheduler refactor.
- Existing job implementations may not enforce every scoped dimension even though scope metadata is recorded and displayed.
- Full end-to-end manual launch against real FMP data depends on local API keys and service availability.
