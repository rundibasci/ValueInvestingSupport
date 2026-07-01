# Requirements - Phase PI1: Portfolio Intelligence Backend

## Scope

Add backend portfolio intelligence that extends the existing portfolio and rebalancing workflow with portfolio-level analytics, liquidity diagnostics, benchmark characteristic comparison, and smarter rebalance metadata.

The selected roadmap phase is the first phase without a matching spec directory after the completed JC2 phase. It appears before PI2, PW, RD1, L, RD2, and K in `specs/roadmap.md`, so it is the next phase for this autonomous run.

## In Scope

- Add `GET /api/v1/portfolios/{id}/analytics`.
- Compute weighted-average portfolio metrics where source data is available:
  - Margin of safety.
  - P/E.
  - Dividend yield.
  - Value score.
  - Piotroski F-Score.
  - ROIC, ROE, and earnings quality.
- Compute sector concentration with a warning when a sector exceeds 40%.
- Compute holding concentration with warnings below 3% and above 20%.
- Compute weighted moat profile percentages for wide, narrow, none, and unknown classifications.
- Add liquidity assessment per holding based on average daily dollar volume and a default 10% participation rate.
- Persist portfolio analytics snapshots for historical tracking.
- Add benchmark characteristic comparison using SPY as the default benchmark when no explicit benchmark symbol is supplied.
- Enhance rebalance responses with urgency, estimated cost, total estimated cost, and holding-period flags.
- Add focused unit tests for calculations and thresholds.

## Exclusions

- No frontend analytics dashboard work; that belongs to PI2.
- No real return tracking or historical benchmark performance comparison.
- No tax calculation beyond short-term/long-term holding-period flags.
- No order execution or brokerage workflow.
- No provider API changes unless needed to read already persisted quote, ratio, valuation, and score data.

## Decisions

- Analytics are read-only and decision-support oriented. Warnings describe concentration, liquidity, or data coverage; they do not recommend trades.
- Missing market or fundamental data should produce null metrics or explicit warning/details rather than blocking the entire analytics response.
- Snapshot persistence stores the computed aggregate payload and core totals so historical analytics can be compared later without over-normalizing this phase.
- Benchmark comparison is characteristic-based only: valuation/yield/sector weights, not total return.
- Rebalancing urgency uses the roadmap defaults:
  - `MUST`: target or concentration constraint breach.
  - `COULD`: drift inside hard constraints but outside a default 3 percentage-point tolerance.
  - `HOLD`: inside tolerance.
- Transaction cost defaults to 0.1% of absolute trade value.
- Minimum position size defaults to 3% and produces a warning only.

## Assumptions

- Existing holdings already have enough quantity/price data to derive current position values.
- Existing security, valuation, score, ratio, and quote entities are the source of truth for portfolio characteristics.
- If moat classification is not yet persisted, the analytics response should classify it as `UNKNOWN` rather than inventing one.
- Acquisition date is represented by holding creation metadata when a dedicated acquisition date is not available.
- The default benchmark symbol `SPY` may not exist in local data. In that case, benchmark comparison returns available portfolio metrics and a data-unavailable marker for benchmark metrics.

## Dependencies

- Existing portfolio CRUD and rebalance endpoints.
- Existing security profile, quote, ratio, valuation, score, and portfolio repositories.
- Flyway for analytics snapshot persistence.
- Spring Boot unit test stack already configured in `backend/pom.xml`.
