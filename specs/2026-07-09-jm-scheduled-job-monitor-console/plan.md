# Plan - Phase JM: Scheduled Job Monitor Console

1. Backend monitor readiness.
   - Add monitor DTOs for job rows and duplicate-run conflicts.
   - Add repository lookup for currently running job runs.
   - Add `GET /api/v1/admin/jobs/monitor`.
   - Add duplicate-run protection to manual job trigger.
   - Add structured logs for run-now attempts, starts, skips, duplicate blocks, and setting changes.

2. Frontend API client.
   - Add `frontend/src/api/adminJobs.ts` with job monitor, run-now, status, history, events, enable, and cron helpers.
   - Model nullable backend fields explicitly so the UI handles missing history and partial status.

3. React ADMIN job monitor page.
   - Add `AdminJobsPage` under `/admin/jobs`.
   - Redirect non-admin users away from the route.
   - Show a dense monitor table with enabled state, cron, next run, current run, last run, records, source, and error summary.
   - Add filters for status and enabled state.
   - Add row controls for run now, enable/disable, history, and ingestion events.
   - Add scoped run inputs and a progress panel that polls by `jobRunId`.

4. Navigation and route wiring.
   - Add the page to `App.tsx`.
   - Add an ADMIN navigation item in `AppShell`.

5. Validation.
   - Add/extend backend tests for monitor response and duplicate-run protection.
   - Run targeted Maven tests for `JobAdminServiceTest`.
   - Run frontend typecheck/build.
   - Review `git diff` for scope and transient artifacts.
