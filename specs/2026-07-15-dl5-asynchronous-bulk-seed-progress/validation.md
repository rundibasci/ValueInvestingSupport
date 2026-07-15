# DL5 — Validation

## Acceptance Criteria

- [ ] Lists at or below the configured threshold retain the existing synchronous `200` behavior.
- [ ] Lists above the threshold return `202` promptly with a stable `seedRunId` and progress link.
- [ ] CSV, ADMIN pack, and criteria-curation flows use the same normalization and threshold rules.
- [ ] Progress state is durable independently from the initiating HTTP request.
- [ ] Counts are monotonic and reconcile exactly with normalized submitted symbols.
- [ ] `seeded_partial` is reported separately from full success and failure.
- [ ] Equivalent active submissions join one run and do not duplicate provider work.
- [ ] Retry processes only failed or interrupted symbols.
- [ ] Refreshing or leaving the page does not cancel the run or lose inspectability.
- [ ] Polling stops at a terminal state and uses bounded backoff during execution/errors.
- [ ] Completed outcomes trigger appropriate research/universe query invalidation.
- [ ] Ownership-safe authorization prevents inspection or retry of another user's run.
- [ ] Executor concurrency and queue capacity are bounded and testable.
- [ ] Interrupted runs never remain falsely `RUNNING` or become falsely successful.
- [ ] No secrets, raw provider payloads, stack traces, or credentials appear in APIs, logs, or stored outcomes.

## Backend Test Matrix

| Scenario | Expected result |
|---|---|
| Empty/invalid ticker list | Validation error before queueing |
| Duplicate/case-varied symbols | One normalized ordered symbol per ticker |
| Small list | Synchronous `200` with unchanged `SeedResult[]` semantics |
| List exactly at threshold | Synchronous path |
| List above threshold | Fast `202` with persisted `QUEUED` run |
| Oversized list | Explicit bounded validation failure |
| Same user/scope/fingerprint while active | Existing run returned; no duplicate work |
| Same symbols after terminal state | New run allowed according to refresh semantics |
| Different users submit same list | Separate owned runs, shared security writes remain safe |
| Atomic worker claim | Only one worker transitions `QUEUED` to `RUNNING` |
| Full success | Reconciled `SUCCESS` and all safe outcomes available |
| Mixed full/partial/failure | Reconciled `PARTIAL_SUCCESS` with separate counters |
| All failures | Terminal `FAILED` with sanitized reasons |
| Provider exception | Per-symbol failure; later symbols continue |
| Executor queue saturated | Durable rejected/failed state or explicit submission rejection |
| Process restart with active run | Interrupted terminal state; retryable unfinished symbols |
| Retry failures | New run contains no successful/partial symbols |
| Foreign run lookup/retry | Ownership-safe `404` |
| ADMIN pack/curation submission by non-ADMIN | Existing authorization response |
| Retention cleanup | Old terminal runs removed; active/recent runs retained |
| Fallback used | Safe provider/fallback context correlated to run ID |

## Frontend Test Matrix

| Scenario | Expected result |
|---|---|
| Synchronous response | Existing result table shown; no polling starts |
| Async acceptance | Progress UI starts with returned run ID |
| Running progress | Bar and counts update monotonically with bounded polling |
| Temporary polling error | Understandable error/retry; run ID retained |
| Terminal success | Polling stops and universe/search queries invalidate |
| Terminal partial success | Partial and failed symbols remain distinguishable |
| Terminal failure | Sanitized failures and retry-failures action shown |
| Retry failures | New run ID tracked; successful symbols stated as excluded |
| Page reload/navigation | Stored active run resumes after authorization check |
| Foreign/expired stored run | Storage cleared safely after `404`; new submission remains possible |
| Duplicate submit while active | Existing run is shown without parallel UI execution |
| Long outcome list | Paginated/virtualized rendering remains usable |
| Keyboard/screen reader | Progress status, counts, errors, and retry controls are perceivable |

## Persistence and Concurrency Checks

- [ ] Flyway migration applies to clean and existing PostgreSQL databases.
- [ ] Database constraints reject invalid counters and duplicate ordered outcomes.
- [ ] Active-run idempotency is safe under concurrent submissions.
- [ ] Run claiming is safe with multiple application workers/instances.
- [ ] Per-symbol writes and progress updates commit independently.
- [ ] Restart recovery is idempotent.
- [ ] Cleanup does not race with active progress reads/writes.

## Regression Checks

- [ ] Existing small CSV seeding response is backward compatible.
- [ ] ADMIN named packs and universe preview remain available.
- [ ] Criteria-based seed retains the reviewed preview context.
- [ ] Full, partial, unavailable, and failed `SeedResult` classifications remain unchanged.
- [ ] Successfully seeded symbols remain discoverable by every authenticated user.
- [ ] Watchlists and portfolios are not mutated by seeding.
- [ ] Yahoo fallback observability and provider provenance remain visible.
- [ ] Pipeline/internal synchronous seeding callers continue to work.

## Verification Commands

Use the repository commands confirmed during implementation and record exact results. At minimum:

```bash
cd backend && ./mvnw test
cd frontend && npm test -- --run
cd frontend && npm run typecheck
cd frontend && npm run build
git diff --check
git status --short
```

Run PostgreSQL-specific migration/concurrency coverage through the repository's Docker Compose test workflow. If the frontend still has no configured test runner, record that limitation explicitly rather than claiming component-test coverage.

## Manual Validation

1. Submit a list below the threshold and confirm the existing synchronous result UX.
2. Submit a long CSV list and confirm the HTTP request returns promptly with a run ID.
3. Observe monotonic progress, navigate away, refresh, and confirm the same run resumes.
4. Produce mixed success/partial/failure outcomes and reconcile every count with the normalized input.
5. Retry failures and confirm previously successful/partial symbols are absent from the new run.
6. Repeat with an ADMIN named pack and criteria-curated universe.
7. Attempt to inspect another user's run and confirm ownership-safe behavior.
8. Restart the backend during an active run and confirm it becomes explicitly interrupted/retryable rather than falsely successful.

## Merge Gate

The feature can be merged only when acceptance, persistence, authorization, concurrency, polling, retry, retention, and regression checks pass; migration and production frontend build succeed; secrets/raw payloads are absent; known unrelated failures are evidenced; and the diff contains no unrelated changes.

## Validation Evidence

- Backend targeted tests: PASS — `SeedRunServiceTest`, `SeedControllerTest`,
  `UniverseSeedControllerTest`, and `UniverseSelectionControllerTest` (11 tests).
- Backend complete suite: 403 tests executed; 402 passed and one known unrelated
  assertion failed in `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable`
  (expected `KO`, received an empty list).
- PostgreSQL migration/runtime validation: PASS — Docker Compose PostgreSQL and Redis
  started, Flyway applied migrations V21–V23, Hibernate validated the schema, and the
  backend started successfully on PostgreSQL. The worker uses a conditional atomic
  `QUEUED` to `RUNNING` update; a dedicated multi-instance concurrency integration test
  remains outstanding.
- Frontend tests: not available because the frontend package has no configured test runner.
- Typecheck/build: PASS — `docker compose build frontend` ran `tsc -b` and the Vite
  production build successfully.
- Backend image/runtime: PASS — `docker compose build backend` and backend container startup.
- Diff hygiene: PASS — `git diff --check`.
- Manual walkthrough: not run against a complete long provider-backed seed list during
  this implementation pass.
- Known environment note: Mockito/Byte Buddy requires running tests outside the restricted
  sandbox on JDK 26 so its inline Java agent can attach.
