# Plan - Phase JC2: Job Scheduling Runtime Control

1. Inspect current job-control implementation.
   - Read `JobAdminController`, `JobAdminService`, `JobDefinition`, job scheduling classes, `JobRunLog`, and repositories.
   - Identify how manual job runs are currently logged and triggered.
   - Confirm static demo page locations.

2. Add persisted runtime settings.
   - Add `JobRuntimeSetting` entity and repository.
   - Add a Flyway migration for job name, enabled flag, cron expression override, timestamps, and indexes.
   - Merge runtime settings with registered `JobDefinition` values in job list responses.

3. Add runtime-control APIs.
   - Add request DTOs for enabled, cron, and scoped run requests.
   - Implement `PUT /api/v1/admin/jobs/{jobName}/enabled`.
   - Implement `PUT /api/v1/admin/jobs/{jobName}/cron`.
   - Validate cron expressions before persistence.

4. Enhance manual run and status visibility.
   - Extend `POST /api/v1/admin/jobs/{jobName}/run` to accept optional scope fields and return `jobRunId`.
   - Add `GET /api/v1/admin/jobs/runs/{jobRunId}/status`.
   - Include processed count, total count when available, elapsed time, and error count.

5. Update demo surfaces.
   - Add job enable/disable, cron update, scoped trigger, run status, history, and event browser affordances to `feature-demo.html` and `full-demo.html` where present.
   - Keep updates static and dependency-free.

6. Tests and validation.
   - Add focused backend tests for persisted settings, cron validation, scoped trigger response, and run-status mapping.
   - Run targeted Maven tests for job admin.
   - Run frontend/static checks only if the changed demo pages have available validation scripts.
