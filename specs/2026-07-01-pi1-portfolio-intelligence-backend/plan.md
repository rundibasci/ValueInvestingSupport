# Plan - Phase PI1: Portfolio Intelligence Backend

1. Inspect portfolio and market-data persistence.
   - Read portfolio entities, repositories, controller, service, and rebalance service.
   - Read security, quote, ratio, valuation, and score entities/repositories used for analytics inputs.
   - Confirm current DTO naming and response style.

2. Add analytics snapshot persistence.
   - Add a `PortfolioAnalyticsSnapshot` entity and repository.
   - Add Flyway migrations for PostgreSQL and H2 if this repository keeps paired H2 migrations.
   - Store portfolio ID, captured timestamp, total market value, benchmark symbol, warning counts, and a JSON/text payload for computed analytics.

3. Add portfolio analytics DTOs and services.
   - Add analytics response records for weighted metrics, sector concentration, holding concentration, moat profile, quality distribution, liquidity results, benchmark comparison, and warnings.
   - Implement `PortfolioAnalyticsService.analyze(portfolioId)`.
   - Implement `LiquidityService.assess(symbol, positionValue)` with the 10% participation default and liquid/moderate/illiquid thresholds.
   - Implement `BenchmarkService.compare(portfolioId, benchmarkSymbol)` using available local benchmark security data.

4. Expose analytics API.
   - Add `GET /api/v1/portfolios/{id}/analytics` to `PortfolioController`.
   - Keep authorization behavior aligned with existing portfolio endpoints.
   - Return partial analytics with explicit missing-data warnings where needed.

5. Enhance rebalance intelligence.
   - Extend rebalance DTOs with urgency, estimated transaction cost, total estimated transaction cost, holding period classification, and minimum-position warnings.
   - Update `PortfolioRebalanceService` calculations with default drift tolerance, cost rate, and minimum position size.
   - Preserve existing response fields for compatibility.

6. Add focused tests.
   - Add unit tests for weighted-average calculations and missing-data handling.
   - Add unit tests for liquidity classification thresholds.
   - Add unit tests for rebalance urgency and cost estimation.
   - Add API/service smoke coverage where existing test patterns make it practical.

7. Validate and document.
   - Run targeted Maven tests for portfolio analytics and rebalancing.
   - Run backend test suite if targeted tests pass quickly enough.
   - Update this spec if implementation constraints require a scoped adjustment.
