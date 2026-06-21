# Requirements — Group F3: Portfolio Builder (Simulation) (M6)

## Scope

Implement the F3 portfolio-allocation simulation promised by the roadmap. An authenticated portfolio owner can request a proposed allocation from their watchlist without mutating the portfolio or triggering live market-data requests.

| Endpoint | Purpose |
|---|---|
| `POST /api/v1/portfolios/{id}/simulate` | Produce a constraint-compliant, value-score-weighted allocation proposal. |

The existing `GET /api/v1/watchlist` and `GET /api/v1/portfolios/{id}` endpoints are the supporting read APIs: they expose the candidate universe and the current portfolio state. No additional endpoint is needed to consume a simulation because the POST response is self-contained.

## Context

F1 supplies a user-owned watchlist and F2 supplies user-owned portfolios, holdings, local price enrichment, and ownership-safe portfolio lookup. D1/D2 supply persisted `ValueScore` and `ValuationResult` data. F3 joins these local data sets to serve the portfolio-construction step of the value-investing cycle.

In line with the mission and tech stack:

- this is decision support, not investment advice; every response includes the MiFID II disclaimer;
- all prices, scores, valuations, sector, and country data come from PostgreSQL, never a request-time FMP/Yahoo call;
- missing or stale local data is explicit rather than silently guessed;
- simulations are transient: they neither create holdings nor overwrite an existing portfolio.

## Decisions

### Allocation objective: normalized Value Score

Eligible candidates are ranked by persisted `ValueScore.totalScore` descending. The initial target weight is each candidate's positive score divided by the sum of positive eligible scores, multiplied by 100. Constraint enforcement then redistributes any excess to remaining eligible candidates in score order. A target-yield field is accepted as an optional minimum eligibility filter, not an optimization objective.

### Candidate universe and eligibility

The candidate universe is the authenticated owner's watchlist. A candidate is eligible only when it has all of:

- a current local `PriceQuote` with positive close price;
- a latest `ValueScore` with positive `totalScore`;
- a latest `ValuationResult` with non-null `marginOfSafety`;
- a known sector and country on `Security`.

Ineligible watchlist symbols are returned in `excludedSymbols` with a machine-readable reason. The request fails with `422 Unprocessable Entity` when no candidates remain after filters and constraints.

### Inputs and constraints

`SimulationRequest` contains:

```json
{
  "budget": 10000.00,
  "maxStockPercent": 25.00,
  "maxSectorPercent": 40.00,
  "maxCountryPercent": 50.00,
  "minimumMarginOfSafety": 0.00,
  "minimumDividendYield": null
}
```

- `budget` is required and positive, in the portfolio's chosen currency (no FX conversion in this phase).
- Constraint fields are optional. Defaults are roadmap defaults: stock 25%, sector 40%, country 50%.
- All percentage constraints must be `> 0` and `<= 100`; the two filters may be zero or positive.
- Fractional shares are not proposed. Each allocated amount is rounded down to a whole-share quantity; residual cash is reported.
- There is no risk-profile enum in this phase: the explicit request constraints express risk tolerance while avoiding an unexplained scoring heuristic.

### Response and infeasibility

The response reports the request parameters, proposal rows, invested amount, unallocated cash, weighted margin of safety, weighted dividend yield when data exists, and actual sector/country weights. A row has symbol, score, price, proposed shares, target amount, actual amount, actual weight, sector, country, margin of safety, and dividend yield.

If a candidate cannot receive even one share within the budget or the remaining constraints, it is skipped with a reason. If the result invests no money, return `422`; do not return a misleading empty recommendation. Constraint caps are never exceeded in order to spend the full budget.

### Security and ownership

All roles (`ADMIN`, `ADVISOR`, `INVESTOR`) may simulate their own portfolio. Resolve the portfolio through `findByIdAndUser`; an unknown or other user's portfolio is a `404`, never a `403`.

## Out of Scope

- Persisting, applying, or automatically executing a proposal; the user edits holdings through F2.
- Rebalancing an existing portfolio (F4).
- Live data calls, cached simulation persistence, FX conversion, tax, fees, fractional shares, or portfolio accounting.
- Adding a frontend; PFD1/H5 will expose this API visually.
