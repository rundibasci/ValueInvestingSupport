# Requirements — Group F4: Portfolio Rebalancing (M6)

## Scope

Implement the roadmap's rebalancing capability for an authenticated portfolio owner. The feature produces a transparent trade proposal, allows the owner to save it for review, and lets the owner explicitly apply a saved proposal to portfolio holdings.

| Endpoint | Purpose |
|---|---|
| `GET /api/v1/portfolios/{id}/rebalance` | Generate a current rebalance proposal using a simulation-derived target allocation or an explicit target allocation. |
| `POST /api/v1/portfolios/{id}/rebalance` | Save a generated or explicitly supplied rebalance proposal for review. |
| `GET /api/v1/portfolios/{id}/rebalances/{rebalanceId}` | Retrieve a saved proposal and its status. |
| `POST /api/v1/portfolios/{id}/rebalances/{rebalanceId}/apply` | Explicitly apply a pending proposal by updating holdings; no brokerage order is sent. |

## Context

F1 provides the user-owned watchlist; F2 provides portfolios and holdings; F3 calculates a constraint-compliant target allocation. F4 completes the portfolio-construction loop by showing the difference between current holdings and an intended allocation, then applying that change only after an explicit save-and-apply action.

This remains a decision-support feature, not order execution or investment advice. The MiFID II disclaimer is mandatory on every proposal response. Prices, scores, and security metadata remain local PostgreSQL data; no rebalancing request may make live FMP or Yahoo calls.

## Decisions

### Target allocation sources

Both target sources are supported:

- **Simulation-derived:** the request carries the same budget and constraints as F3. The service uses the F3 allocation logic to produce target weights and amounts from the owner's eligible watchlist.
- **Explicit:** the request carries target weights by symbol. Weights must be positive, total at most 100%, reference securities known locally, and comply with the configured per-stock, sector, and country caps.

The request selects exactly one source. Explicit targets are valuable when an advisor has a considered allocation; simulation-derived targets preserve the existing score-led workflow.

### Proposal content and trade policy

For each affected symbol, return current shares/value/weight, target shares/value/weight, delta shares, estimated trade value, side (`BUY`, `SELL`, or `HOLD`), and the local price used. The response includes projected residual cash and aggregate projected sector/country weights.

Share quantities are whole numbers. A configurable `minimumTradeValue` defaults to zero; trades below it are suppressed and reported as skipped. Selling never reduces a holding below zero. Apply operations use the prices captured when the proposal is saved, rather than silently recalculating them.

### Save and apply lifecycle

A generated proposal may be saved as `PENDING`. Applying it is a separate, authenticated owner action and is idempotency-protected: a successful proposal becomes `APPLIED` and cannot be applied twice. The apply action changes local holdings only—creating, updating, or deleting zero-share holdings as needed—and records `appliedAt`. No external brokerage order, cash ledger, tax, fee, or FX processing is in scope.

### Security, consistency, and errors

All roles may manage only their own portfolios; another owner's resource is indistinguishable from absent (`404`). Persist proposals and their lines in PostgreSQL through Flyway migrations. A saved proposal is rejected at apply time with `409 Conflict` if its portfolio holdings have changed since it was generated, preventing stale proposals from overwriting newer decisions. Invalid target configuration is `400`; an infeasible target/allocation is `422`.

## Out of Scope

- Brokerage integration, order routing, automatic execution, taxes, commissions, FX, fractional shares, and portfolio accounting.
- Live market-data reads or changing F3's allocation algorithm.
- Frontend work; PFD1/H5 will expose this workflow visually.
