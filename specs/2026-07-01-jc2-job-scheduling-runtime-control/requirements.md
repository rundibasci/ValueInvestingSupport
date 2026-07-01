# Requirements - Phase JC2: Job Scheduling Runtime Control

## Scope

Implement the first runtime-control layer for scheduled jobs after JC1's job history and ingestion event API:

- Persist per-job runtime settings so enabled/disabled state and cron overrides survive restarts.
- Add admin APIs to update enabled state and cron expressions.
- Enhance manual job triggering to accept scoped symbols, exchange, and data types and return a pollable job run ID.
- Add a run-status endpoint for manually triggered runs using existing `JobRunLog` state where possible.
- Update static demo pages with job-control controls that call the new APIs.

## Exclusions

- No distributed scheduler replacement or cluster coordination in this phase.
- No Cloud Run Jobs or Cloud Scheduler work; that belongs to Group K.
- No broad refactor of ingestion implementations beyond the minimum needed to accept scoped trigger metadata.
- No investment-advice language changes outside job-control surfaces.

## Decisions

- Use the existing JC1 `JobRunLog`, `IngestionEvent`, `JobAdminController`, and `JobAdminService` boundaries.
- Store runtime job settings in a new relational table keyed by job name.
- Validate cron expressions with Spring's cron parser before persisting.
- Return job-run status from persisted run logs and ingestion-event counts rather than introducing a separate in-memory status store.
- For scoped manual triggers, persist the requested scope in the run log when the current job implementation cannot yet enforce every scope dimension.

## Assumptions

- The earliest unstarted roadmap phase is JC2 because `specs/2026-06-30-jc1-job-run-history-ingestion-event-api` exists and no JC2 spec directory exists.
- Existing runtime log files in the repository root are unrelated to this phase and should not be committed.
- If existing scheduled methods are fixed-rate or fixed-cron annotations, this phase can make admin-visible persisted settings available and enforce disabled state on manual execution first, while documenting any deferred dynamic rescheduling limitation.
- Existing demo pages can be updated without redesigning their full layout.

## Dependencies

- Spring Boot 3.x and Java 21 backend.
- Flyway migrations under `backend/src/main/resources/db/migration`.
- Existing admin job API from JC1.
- Existing `feature-demo.html` and `full-demo.html` static resources if present.
