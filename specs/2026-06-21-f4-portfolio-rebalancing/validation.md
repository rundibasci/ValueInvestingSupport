# Validation — Group F4: Portfolio Rebalancing (M6)

## Merge gates

### 1. Unit tests

```bash
mvn test -pl backend -Dtest="PortfolioRebalanceServiceTest,PortfolioControllerTest"
```

Verify deterministic fixtures cover both target sources, cap validation, integer-share target calculation, buy/sell/hold deltas, minimum-trade suppression, residual cash, and `422` infeasibility. Verify save/apply lifecycle behavior, an applied proposal cannot be applied twice, and stale holdings return `409`.

### 2. API contract and authorization tests

- An authenticated owner can generate, save, retrieve, and apply a proposal.
- Missing/invalid request data returns `400`; no token returns `401`; other-user portfolio/proposal access returns `404`.
- Explicit targets exceeding 100% or constraint caps return `400`; an otherwise valid but unachievable allocation returns `422`.
- Proposal responses expose current and target values, deltas, captured prices, skipped trades, aggregates, lifecycle state, and the MiFID II disclaimer.

### 3. PostgreSQL integration test

```bash
mvn test -pl backend -Dtest=PortfolioRebalanceIT
```

Seed an owner, a portfolio with several holdings, local price/score/valuation data, and a watchlist. Then:

1. Generate and save one simulation-derived proposal and one explicit-target proposal.
2. Apply a pending proposal and verify holding share counts, created/deleted zero-share rows, and `APPLIED` status in PostgreSQL.
3. Apply it again and assert `409` with no further holdings changes.
4. Change holdings after saving a second proposal; applying it must return `409` and preserve the changed holdings.
5. Authenticate a second user and assert `404` for the owner's portfolio and proposal.

### 4. Regression suite

```bash
mvn test -pl backend
```

All existing authentication, watchlist, scoring, security-detail, portfolio CRUD, and F3 simulation tests remain green.

### 5. Manual smoke test

With seeded local data and a valid JWT, generate a proposal, save it, then apply it. Confirm that the response discloses the captured prices and trade deltas, no live market-data call occurs, and a subsequent portfolio read matches the applied target holdings.

## Ready to merge when

Unit and PostgreSQL integration tests pass, the full backend suite is green, apply is transactional and safe against duplicate/stale execution, all operations are ownership-safe, and every decision-support proposal includes the MiFID II disclaimer.
