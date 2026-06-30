# JC1: Job Run History & Ingestion Event API Requirements

## Scope

Phase JC1 adds admin-readable ingestion observability around the existing job system:

- List registered jobs with schedule, enabled status, and latest run summary.
- Query paginated run history for a named job.
- Persist per-symbol ingestion events for job work that handles individual securities or data types.
- Query ingestion events by job run, symbol, and status.
- Preserve the current job trigger behavior and authorization model.

## Roadmap Context

This is the first phase in Group JC, after J3. The roadmap selects JC1 before JC2 so that runtime controls added later have visible history and per-symbol diagnostics to validate against.

## Decisions

- Reuse existing `JobRunLog` persistence instead of introducing a parallel run-history store.
- Add a dedicated `IngestionEvent` entity and repository because per-symbol job diagnostics are a distinct append-only audit stream.
- Expose endpoints under the existing admin API surface at `/api/v1/admin/jobs`.
- Keep event creation optional for jobs that do not process symbols; those jobs still appear in job history.
- Return DTOs that are stable for frontend and demo-page use rather than leaking JPA entities.

## Exclusions

- Runtime enable/disable controls, cron editing, and scoped partial job triggers are deferred to JC2.
- No changes to external market-data provider contracts.
- No user-facing non-admin job views.
- No destructive cleanup policy for historical events in this phase.

## Assumptions

- Existing job execution already records enough data in `JobRunLog` to support history summaries.
- Job names used by manual triggers are the canonical names for the JC1 API.
- Existing ADMIN security rules protect `/api/v1/admin/**`; endpoint tests should still verify authorization where practical.
- Jobs that already process tickers can emit events at natural success/failure boundaries without changing financial calculations.

## Dependencies

- Spring Boot backend with JPA/Flyway.
- Existing job services, job run log entity, and admin job trigger endpoint.
- Existing security test helpers for ADMIN-authenticated MockMvc tests.
