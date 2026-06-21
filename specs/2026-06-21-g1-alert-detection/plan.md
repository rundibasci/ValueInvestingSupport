# Plan — Group G1: Alert Detection Job

## Task Group 1: Confirm the data contract and persistence model

1.1 Inspect the existing `Alert`, watchlist, portfolio, quote, valuation, fundamental, dividend, and insider entities/repositories.

1.2 Map each of the eight `AlertType` values to the exact persisted inputs and define the observed value saved with the alert.

1.3 Add a Flyway migration only if the existing alert table cannot retain the factual trigger context or support race-safe same-day deduplication.

1.4 Add repository queries/indexes for efficient user-symbol-type-date deduplication and for loading nightly job candidates.

## Task Group 2: Build deterministic rule evaluators

2.1 Create focused rule evaluators/services for MoS entry/exit, 5% price movement, fundamental degradation, dividend cuts, insider sells, earnings deterioration, and rebalancing.

2.2 Reuse the existing valuation and portfolio-rebalance services rather than duplicating financial calculations.

2.3 Make null/insufficient history an explicit non-trigger result with a reason suitable for diagnostics.

2.4 Ensure all results include alert type, user, symbol, threshold, observed value, and evaluation date.

## Task Group 3: Orchestrate nightly detection

3.1 Implement an `AlertDetectionJob` using Spring scheduling and configurable cron/timezone settings.

3.2 Collect the union of user-owned watchlist and portfolio symbols, evaluate applicable rules, and persist new `ACTIVE` alerts.

3.3 Enforce idempotency for repeated job invocation and same-day conditions, including concurrent execution protection where needed.

3.4 Continue processing after per-symbol/user failures and add structured logging plus job-health/metrics integration consistent with existing ingestion jobs.

## Task Group 4: Unit tests

4.1 Test every alert rule at its boundary, including the 5.00% price movement trigger and a 4.99% non-trigger.

4.2 Test missing thresholds, missing/inadequate histories, inactive/missing data, and no-calculation cases do not emit false alerts.

4.3 Test each rule emits an active, user-scoped record with an accurate type, threshold, symbol, timestamp/date, and factual context.

4.4 Test same user/symbol/type/day deduplication, while allowing different types, users, symbols, or later days to persist independently.

## Task Group 5: Integration and scheduled-job tests

5.1 Add a Testcontainers PostgreSQL integration test that seeds representative data for all eight types and invokes the job directly.

5.2 Assert expected alerts persist, are available through the existing active-alert query, and remain user-isolated.

5.3 Invoke the job twice with unchanged data and assert no duplicate rows are added.

5.4 Verify one malformed or incomplete candidate does not prevent an unrelated valid candidate from creating an alert.

## Task Group 6: Review and merge readiness

6.1 Run the targeted unit and integration tests, then `mvn test -pl backend`.

6.2 Verify Flyway migration(s) apply cleanly on a fresh PostgreSQL database.

6.3 Confirm no secrets, market-data credentials, or recommendation language appear in source, fixtures, logs, or documentation.

6.4 Perform a manual scheduled-run smoke test and record results in `validation.md` before merging.
