# DL5 — Implementation Plan

## 1. Contracts, Configuration, and Migration

1. Define normalized submission, synchronous result, async acceptance, progress, outcome-page, and retry contracts.
2. Add configuration properties for async threshold, maximum symbols, executor concurrency/queue capacity, retention days, polling hints, and progress update batch size.
3. Add Flyway migrations for seed-run and ordered seed-run-outcome tables with ownership, lifecycle, counters, request fingerprint, timestamps, safe result fields, indexes, and invariants.
4. Add JPA entities, repositories, lifecycle enums, and ownership-safe lookup methods.
5. Document the lifecycle transition table and count reconciliation rules.

## 2. Normalization and Submission Orchestration

1. Centralize ticker normalization and validation so all seed entry points use identical rules.
2. Introduce a submission service that chooses synchronous or asynchronous execution after normalization.
3. Preserve the existing synchronous `SeedResult[]` contract at or below the configured threshold.
4. Atomically find-or-create an active run using the user/scope/request fingerprint idempotency key.
5. Return `202 Accepted` with stable run links for long lists and preserve criteria-preview context for universe curation.
6. Apply explicit maximum-size and empty-list validation before any work is queued.

## 3. Durable Worker and Progress Accounting

1. Configure a named bounded Spring task executor dedicated to bulk seed runs.
2. Atomically claim `QUEUED` runs and transition them to `RUNNING` before processing.
3. Invoke the existing per-ticker seed path sequentially or at the configured safe concurrency without changing its transaction isolation.
4. Persist each sanitized outcome and update monotonic counters in independent transactions.
5. Derive `SUCCESS`, `PARTIAL_SUCCESS`, or `FAILED` from reconciled terminal outcomes.
6. Mark abandoned active runs as interrupted during startup recovery and retain unfinished symbols for explicit retry.
7. Ensure executor rejection leaves a durable, understandable state rather than a permanently queued run.

## 4. Authorization, Idempotency, and Retry

1. Attach the authenticated user and submission scope to every run.
2. Enforce ownership-safe `404` semantics on progress, outcomes, and retry endpoints.
3. Preserve existing role rules for CSV, ADMIN pack, and ADMIN universe-curation submission.
4. Join equivalent active submissions and prevent concurrent duplicate symbol processing.
5. Implement retry-failures as a new run containing only failed/interrupted outcomes.
6. Test database-level claim behavior and duplicate submissions from simultaneous requests.

## 5. Progress and Outcome APIs

1. Add authenticated progress, paginated outcomes, recent-run history if required by recovery UX, and retry endpoints.
2. Expose exact reconciled counts, lifecycle status, timestamps, safe current symbol, terminal reason, and polling hint.
3. Map existing full, partial, unavailable, and failed seed results to stable public outcome codes.
4. Include safe provider/fallback provenance already available from seed results and observability correlation IDs.
5. Sanitize errors centrally and add serialization tests proving secrets/raw payloads are absent.

## 6. Existing Seed Endpoint Integration

1. Adapt `/api/v1/universe/seed` and `/api/v1/admin/seed` to the threshold-aware submission service.
2. Adapt criteria-based universe seeding while preserving its preview response semantics.
3. Keep pipeline/internal callers synchronous unless explicitly migrated and tested.
4. Preserve small-list behavior, partial seed persistence, source fields, and downstream query availability.
5. Add API documentation/examples for both `200` and `202` response paths.

## 7. Frontend API and Polling Model

1. Add TypeScript discriminated response types for synchronous completion and async acceptance.
2. Add progress, paginated outcomes, recent-run recovery if needed, and retry API methods.
3. Implement a reusable seed-run hook using TanStack Query bounded backoff and terminal-state stop rules.
4. Persist active run IDs per seed surface and clear them only after terminal state is displayed or dismissed.
5. Invalidate research/universe queries at bounded progress milestones and terminal completion.
6. Handle `404` expired/foreign runs, transient API errors, retry, and browser reload recovery.

## 8. Seed Universe and Curation UX

1. Add accessible progress panels to CSV, ADMIN pack, and criteria-curation flows.
2. Display lifecycle state, progress bar, exact counts, elapsed/update times, and safe current symbol.
3. Render full/partial/failure outcomes with source and Yahoo fallback context without exposing raw provider data.
4. Paginate long outcome lists and make failures easy to inspect without rendering hundreds of rows at once.
5. Add retry-failures controls that state successful symbols are excluded.
6. Keep forms usable after terminal state and prevent duplicate active submission UI.

## 9. Retention and Operational Observability

1. Add scheduled or startup-triggered cleanup for terminal runs older than the configured retention period.
2. Correlate seed runs with market-data fallback events and existing job/ingestion telemetry where supported.
3. Add structured logs and metrics for queued/running duration, throughput, failures, partial success, executor saturation, and interrupted recovery.
4. Ensure logs and metrics contain run IDs and safe counts, never credentials or raw provider payloads.

## 10. Verification and Merge Readiness

1. Add backend tests for threshold selection, normalization, authorization, idempotency, atomic claim, monotonic progress, partial results, terminal state, restart recovery, retention, retry scope, and executor saturation.
2. Add integration tests using PostgreSQL for locking, uniqueness, migrations, and independent progress transactions.
3. Add frontend tests for polling/backoff, reload recovery, synchronous completion, async completion, partial success, failure, retry, expired runs, and query invalidation.
4. Run targeted tests, complete backend suite, frontend typecheck/build/tests, migration validation, and `git diff --check`.
5. Complete `validation.md`, update the DL5 roadmap status only after acceptance criteria pass, and review the diff for scope, authorization, resilience, and secret safety.
