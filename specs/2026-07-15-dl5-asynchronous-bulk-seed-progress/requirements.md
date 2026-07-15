# DL5 — Asynchronous Bulk Seed Progress

## Context

The shared-universe endpoints currently call `SeedService.seedTickers` inside the initiating HTTP request and return only after every normalized ticker has completed. This is predictable for short lists but makes long CSV lists, ADMIN packs, and criteria-based universe curation appear stalled and vulnerable to request timeouts. Navigating away also loses the only visible execution state.

The codebase already has durable `JobRunLog` and `IngestionEvent` concepts for scheduled/admin jobs, per-ticker transaction isolation in `SeedTickerService`, synchronous `SeedResult` semantics including partial success, and frontend server-state management through TanStack Query. DL5 must reuse compatible patterns while keeping user-owned seed-run authorization and result semantics explicit.

This phase implements roadmap phase **DL5: Asynchronous Bulk Seed Progress** in accordance with `specs/mission.md` and `specs/tech-stack.md`. Seeding remains shared reference-data ingestion, not an investment recommendation. Provider provenance, partial-data explanations, and Yahoo fallback observability must remain intact.

## Scope

### Submission behavior

- Normalize symbols consistently before deciding whether execution is synchronous or asynchronous: trim, uppercase, remove blanks, remove duplicates while preserving first-seen order, and validate the documented maximum list size.
- Add a configurable asynchronous threshold with a conservative default. Lists at or below the threshold retain the existing synchronous `200 OK` result contract.
- Lists above the threshold return `202 Accepted` promptly with a stable `seedRunId`, lifecycle status, normalized count, progress URL, and results URL or equivalent links.
- Apply the same threshold behavior to authenticated CSV seeding, ADMIN named-pack seeding, and criteria-based universe seeding.
- Preserve the criteria preview alongside the asynchronous run reference where the curation flow needs it.

### Durable seed-run model

- Add dedicated durable seed-run and per-symbol outcome records rather than treating a user submission as a scheduled job.
- Store the initiating user, run scope/type, normalized request fingerprint, lifecycle status, total and progress counts, timestamps, and sanitized terminal summary.
- Store one ordered outcome per normalized symbol with status, public reason code/message, source/provider context, fallback context where available, and a safe projection of the existing `SeedResult`.
- Use lifecycle states `QUEUED`, `RUNNING`, `PARTIAL_SUCCESS`, `SUCCESS`, and `FAILED`. Do not expose `CANCELLED` until cancellation is implemented.
- Keep counts monotonic and enforce the invariant:
  `processed = succeeded + partiallySeeded + failed` and `processed <= total`.
- Define retention for completed runs and outcomes. Initial default: retain 30 days, configurable, with bounded paginated history and cleanup that never removes active runs.

### Execution and concurrency

- Execute asynchronous runs outside the request thread with a named, bounded executor and configurable concurrency limits.
- Preserve the existing one-symbol-at-a-time transactional boundary and `SeedResult` partial-success semantics.
- Limit concurrency both per run and globally so overlapping users cannot exhaust FMP/Yahoo quotas or database connections.
- Update durable progress after each symbol or a documented small batch, in an independent transaction so navigation or request completion cannot roll it back.
- On application startup, mark abandoned `QUEUED`/`RUNNING` runs as failed with a restart-safe reason, or safely resume them if the implementation can prove idempotent continuation. Initial decision: mark them `FAILED_INTERRUPTED` and offer retry for unfinished symbols; do not silently claim completion.
- Correlate market-data fallback events and ingestion/job telemetry with `seedRunId` where the existing observability schema can be extended safely.

### Authorization and idempotency

- `INVESTOR` and `ADVISOR` users may submit and inspect their own CSV seed runs.
- `ADMIN` users may submit ADMIN pack and universe-curation runs and inspect those runs. ADMIN access to other users' personal submissions is excluded unless an explicit operational endpoint is added and tested.
- Preserve existing shared-universe mutation authorization; the run record is user-owned even though successfully seeded securities become platform-wide reference data.
- Compute idempotency from user, normalized ordered symbol set, and submission scope. If an equivalent active run exists, return that run instead of starting another.
- Retry creates a new run containing only failed or interrupted symbols. Successful and partial-success symbols are not reprocessed automatically.
- Reject oversized submissions and conflicting invalid requests before queueing.

### Progress API

- Expose authenticated endpoints equivalent to:
  - `GET /api/v1/seed/runs/{seedRunId}` for current status and aggregate progress;
  - `GET /api/v1/seed/runs/{seedRunId}/outcomes?page=&size=` for paginated per-symbol outcomes;
  - `POST /api/v1/seed/runs/{seedRunId}/retry-failures` for a scoped retry.
- Return ownership-safe `404` for a run the caller may not inspect.
- Include total, processed, success, partial, failure, current symbol when safe, timestamps, terminal reason, and sanitized outcomes.
- Never expose raw provider payloads, stack traces, API keys, credentials, internal exception class names, or database details.

### Frontend

- Update Seed Universe and Universe Curation to accept either the existing synchronous results or an asynchronous acceptance response.
- Poll non-terminal runs with TanStack Query using bounded backoff and stop automatically at a terminal state.
- Persist the active `seedRunId` per surface in browser storage so refresh or navigation can resume inspection; validate ownership through the API rather than trusting storage.
- Show an accessible progress bar, exact reconciled counts, current phase/symbol when supplied, partial-success explanations, source/fallback context, and paginated failed outcomes.
- Invalidate screener, security search/detail/review, and related universe queries as successful outcomes arrive at bounded intervals and when the run completes.
- Offer retry only for failed/interrupted symbols and clearly show that successful symbols will not be rerun.
- Do not show completion merely because the submission endpoint returned `202`.

## Decisions

1. **Dedicated seed-run persistence.** Reuse `JobRunLog`/`IngestionEvent` patterns and correlation where helpful, but introduce seed-specific tables because job logs lack user ownership, exact progress counters, ordered outcomes, and retry semantics.
2. **Backward-compatible threshold split.** Small lists keep `200` plus `SeedResult[]`; large lists use `202` plus a run descriptor. Frontend clients use a discriminated response adapter rather than guessing from timing.
3. **Default threshold and limits are configuration.** Initial proposed values are async above 10 normalized symbols, maximum 500 symbols, global worker concurrency 2, and per-run symbol concurrency 1. Values must be documented and test-overridable.
4. **Database is the progress source of truth.** Redis may cache reads but cannot be the only run-state store.
5. **At-least-once-safe retry, not transparent restart resume.** Interrupted runs fail explicitly; users retry only unfinished/failed symbols. Existing ticker seeding must remain idempotent enough to refresh shared data safely.
6. **No cancellation in DL5.** Cancellation creates misleading guarantees unless provider calls and transaction boundaries support it reliably.
7. **No duplicate active work.** Equivalent active submissions join the existing run. Non-equivalent submissions are queued subject to bounded executor capacity.
8. **Partial success is a first-class terminal result.** `seeded_partial` contributes to `partiallySeeded`, not `failed`, and remains researchable with unavailable valuation/score fields.
9. **Sanitized domain outcomes only.** Public failure details are stable codes and safe messages derived from current `SeedResult` handling.
10. **Polling, not WebSocket/SSE.** TanStack Query polling matches the current stack and roadmap; push transport is out of scope.

## Out of Scope

- Order execution, investment recommendations, or automatic watchlist/portfolio mutation.
- WebSocket or Server-Sent Events progress delivery.
- User-requested cancellation.
- Distributed queue infrastructure such as Kafka, RabbitMQ, or a cloud task service.
- Parallelizing the internal provider calls for one ticker.
- Changing valuation guardrails, fallback selection, or existing seed result classification.
- Reworking scheduled ingestion jobs or the ADMIN jobs console beyond optional correlation links.
- GCP Cloud Run Jobs migration, which belongs to K2.

## Compatibility and Risks

- Existing synchronous callers must continue to receive the current result body for small lists.
- Criteria-based curation currently returns preview and results together; its async acceptance contract must retain enough preview context for the page.
- In-process asynchronous execution survives HTTP completion but not process termination; durable interrupted-state recovery and explicit retry prevent false success. K2 may later move execution to Cloud Run Jobs.
- Provider quotas and unofficial Yahoo limitations require strict concurrency bounds and no automatic retry storms.
- Persisting full provider responses would create security, storage, and licensing risks; only the safe `SeedResult` projection is stored.
- Progress updates must use independent transactions without weakening per-symbol isolation or holding one long database transaction.
- Multiple API instances could execute the same queued run without an atomic claim. Claiming must use a database lock or compare-and-set status transition.
