# Plan — Group F4: Portfolio Rebalancing (M6)

## Task Group 1: Persistence and domain contracts

1.1 Review F2 holdings and F3 simulation contracts; add Flyway tables/entities for `RebalanceProposal` and proposal lines, including status, captured prices, holdings version/fingerprint, timestamps, and ownership-safe relations.

1.2 Add repository methods to load portfolios, holdings, saved proposals, and lines in bounded query counts.

1.3 Define validated request and response DTOs for simulation-derived targets, explicit targets, minimum trade value, proposal lines, aggregates, lifecycle state, and the MiFID II disclaimer.

## Task Group 2: Rebalance calculation

2.1 Reuse F3 allocation logic for simulation-derived targets; validate explicit target weights and all configured stock, sector, and country caps locally.

2.2 Calculate current weights, target whole-share quantities, delta shares, buys, sells, holds, estimated trade amounts, residual cash, and skipped sub-threshold trades deterministically.

2.3 Return `422` for infeasible targets and preserve no-live-data behavior.

## Task Group 3: Proposal lifecycle

3.1 Add a rebalancing service to generate and save `PENDING` proposals, retaining captured prices and a holdings fingerprint.

3.2 Implement owner-authorized retrieval of saved proposals.

3.3 Implement transactional, idempotency-safe apply logic: verify pending status and unchanged holdings fingerprint, modify holdings, mark the proposal `APPLIED`, and reject stale or already-applied requests appropriately.

## Task Group 4: HTTP API and authorization

4.1 Add the F4 controller endpoints and map validation errors to `400`, absent/foreign resources to `404`, infeasible requests to `422`, and stale/apply conflicts to `409`.

4.2 Ensure all authenticated roles retain access to their own portfolios only, with `401` for unauthenticated requests.

4.3 Include transparent inputs, captured data, formula-relevant values, and the MiFID II disclaimer in every proposal representation.

## Task Group 5: Tests

5.1 Add calculator/service unit tests for both target sources, buy/sell/hold deltas, whole-share rounding, caps, residual cash, threshold skips, and infeasible allocations.

5.2 Add lifecycle unit tests for saving, applying, idempotency, stale-holdings conflict, ownership isolation, and zero-share holding cleanup.

5.3 Add controller tests for contracts, validation, authentication, `404`, `409`, and `422` behavior.

5.4 Add PostgreSQL integration coverage that seeds a portfolio and local market data, saves a proposal, applies it, verifies holdings, and proves no second apply or cross-user access succeeds.

## Task Group 6: Merge readiness

6.1 Run targeted unit and integration tests, then the full backend suite.

6.2 Manually execute generate → save → apply against seeded local data and inspect the resulting portfolio.

6.3 Review transaction safety, ownership isolation, captured-price transparency, disclaimer presence, and absence of live data calls.
