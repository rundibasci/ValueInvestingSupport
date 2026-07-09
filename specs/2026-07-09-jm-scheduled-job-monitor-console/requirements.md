# Requirements - Phase JM: Scheduled Job Monitor Console

## Scope

Implement an ADMIN-facing scheduled-job monitor console on top of the existing JC1/JC2 job-control APIs:

- Provide a monitor-oriented backend response with every registered job, runtime state, last run, latest running run, next schedule metadata when available, and actionable error context.
- Allow an admin to launch any registered scheduled job immediately from the React app and receive a pollable `jobRunId`.
- Prevent duplicate manual runs for the same job when a run is already marked `RUNNING`.
- Add a React ADMIN-only page that shows job status, history, ingestion events, scoped run controls, and run-now progress.
- Hide the route from non-admin navigation and redirect non-admin users away from the page.

## Exclusions

- No distributed scheduler or Cloud Run Jobs work; Group K owns production job execution topology.
- No replacement of fixed `@Scheduled` annotations with dynamic scheduling.
- No broad redesign of existing admin pages or navigation.
- No investment-advice copy changes outside operational job surfaces.
- No new external dependencies unless already present in the project.

## Decisions

- Use existing `JobAdminController`, `JobAdminService`, `JobRunLog`, `IngestionEvent`, and `JobRuntimeSetting` boundaries.
- Add `GET /api/v1/admin/jobs/monitor` rather than requiring the frontend to assemble monitor state from multiple endpoints.
- Reuse `POST /api/v1/admin/jobs/{jobName}/run`, `GET /api/v1/admin/jobs/runs/{jobRunId}/status`, job history, and ingestion event endpoints from JC2.
- Return HTTP `409 Conflict` with the active `jobRunId` when an immediate launch would duplicate a running job.
- Keep frontend state local to the page with React hooks and existing `apiFetch`.

## Assumptions

- JM is the selected phase because the user explicitly requested implementation of the just-added roadmap group, even though the automated skill normally selects the first unstarted phase.
- Existing `JobRunLog.status` values include `RUNNING`, `SUCCESS`, `FAILED`, and `SKIPPED`.
- Next-run calculation can be exposed when the effective cron expression parses successfully; invalid persisted cron values should not break the monitor response.
- The current app shell is the intended React surface for this admin window.
- Runtime audit logging can be represented by structured application logs for this phase; a deeper immutable audit entity can remain in PW/K hardening if needed.

## Dependencies

- Spring Boot 3.x / Java 21 backend.
- Existing JC1/JC2 job admin implementation.
- React 18, TypeScript, React Router, and existing `apiFetch`.
- Existing ADMIN role and `useAuth` session state.
