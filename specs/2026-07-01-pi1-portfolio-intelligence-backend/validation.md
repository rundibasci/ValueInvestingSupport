# Validation - Phase PI1: Portfolio Intelligence Backend

## Acceptance Checks

- `GET /api/v1/portfolios/{id}/analytics` returns portfolio weighted metrics, sector concentration, holding concentration, moat profile, quality distribution, liquidity diagnostics, benchmark comparison, and warnings.
- Analytics tolerate missing local data and make missing inputs visible in warnings or null fields.
- A portfolio analytics snapshot is persisted when analytics are generated.
- Sector concentration flags sectors above 40%.
- Holding concentration flags positions below 3% and above 20%.
- Liquidity classification uses days-to-liquidate thresholds:
  - `LIQUID` for fewer than 5 days.
  - `MODERATE` for 5 to 20 days.
  - `ILLIQUID` for more than 20 days.
- Rebalance responses include urgency, estimated transaction cost per line, total estimated cost, holding period classification, and minimum-position warnings without removing existing fields.
- All copy and enum names remain factual and decision-support oriented.

## Test Strategy

- Run targeted unit tests:
  - `./mvnw.cmd test -Dtest=PortfolioAnalyticsServiceTest,LiquidityServiceTest,PortfolioRebalanceServiceTest`
- If targeted tests pass, run:
  - `./mvnw.cmd test`

## Manual QA

- Review `GET /api/v1/portfolios/{id}/analytics` response shape for a small portfolio with at least two sectors.
- Review rebalance response JSON to confirm old fields are still present and new intelligence fields are additive.
- Review Flyway migrations for PostgreSQL/H2 compatibility.

## Merge Readiness

- Specs exist and are non-empty.
- Backend compiles.
- Targeted portfolio analytics/rebalance tests pass.
- Full backend unit test suite passes or any unrelated pre-existing failures are documented with evidence.
- Worktree contains only this phase's spec, implementation, test, changelog, and vault activity updates plus pre-existing untracked runtime logs.

## Known Risks

- Some roadmap metrics may not have persisted source data yet. Those metrics must remain nullable with explicit coverage warnings.
- Benchmark comparison depends on the benchmark symbol being present in local data.
- Snapshot payload is intentionally coarse in this phase; future phases may normalize it if trend queries require structured fields.
