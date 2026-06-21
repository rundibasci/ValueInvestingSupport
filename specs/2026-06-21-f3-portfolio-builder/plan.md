# Plan — Group F3: Portfolio Builder (Simulation) (M6)

## Task Group 1: Data access and eligibility model

1.1 Review existing Watchlist, Security, ValueScore, ValuationResult, PriceQuote, Portfolio, and User mappings; add only repository queries needed to resolve the latest score, valuation, quote, and the owner's watchlist symbols in bounded query counts.

1.2 Introduce an internal `SimulationCandidate` model containing the local data needed for scoring, constraints, and exclusion reasons.

1.3 Resolve all candidate data locally and classify missing price, non-positive/missing score, missing MoS, and missing sector/country as exclusions.

## Task Group 2: API contracts

2.1 Add validated `SimulationRequest` with budget, optional caps, and optional MoS/yield filters. Apply defaults of 25/40/50 percent in the service.

2.2 Add response DTOs for proposal rows, excluded symbols, aggregate weights, and the complete simulation response, including the MiFID II disclaimer.

2.3 Document deterministic ordering: proposal rows are score-descending then symbol-ascending; exclusions are symbol-ascending.

## Task Group 3: Simulation service

3.1 Extend `PortfolioService` or introduce a focused `PortfolioSimulationService` in `it.mazzoni.vis.portfolio`; reuse F2 user and ownership resolution.

3.2 Filter the authenticated user's watchlist by the request thresholds, returning all rejected candidates with reasons.

3.3 Compute normalized Value Score target weights and allocate whole shares without exceeding per-stock, per-sector, or per-country caps. Redistribute unallocatable weight deterministically among remaining candidates.

3.4 Calculate invested amount, residual cash, actual weights, weighted MoS, and weighted yield. Return `422` when no valid allocation can buy a share.

3.5 Keep the operation read-only (`@Transactional(readOnly = true)`); it must not alter holdings, watchlist entries, or scores.

## Task Group 4: Controller and authorization

4.1 Add `POST /api/v1/portfolios/{id}/simulate` to `PortfolioController`, accepting `@Valid SimulationRequest` and returning `200 OK`.

4.2 Ensure the existing security configuration permits authenticated ADMIN, ADVISOR, and INVESTOR roles and preserves `401` for unauthenticated calls.

4.3 Propagate invalid input as `400`, ownership/not-found as `404`, and no feasible allocation as `422` with a useful problem message.

## Task Group 5: Tests

5.1 Add controller tests for valid response shape, request validation, unauthenticated access, `404`, and `422` propagation.

5.2 Add focused service unit tests for score normalization, each cap, deterministic redistribution, whole-share rounding, residual cash, filter/exclusion reasons, and no mutation.

5.3 Add a Testcontainers PostgreSQL integration test that seeds a user, portfolio, watchlist, securities, quotes, valuations, and scores; verifies a constrained proposal end to end.

## Task Group 6: Review and merge readiness

6.1 Run targeted unit and integration tests, then the complete backend test suite.

6.2 Manually verify a simulation using local seeded data and confirm that portfolio holdings remain unchanged before and after the request.

6.3 Review returned disclaimer, constraint compliance, rounding, ownership isolation, and absence of live market-data client calls before merge.
