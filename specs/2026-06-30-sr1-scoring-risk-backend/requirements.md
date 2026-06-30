# Phase SR1 Requirements - Scoring & Risk Backend

## Scope

- Implement Phase SR1 from `specs/roadmap.md`: backend scoring trust and risk intelligence.
- Keep behavior data-source agnostic by computing from persisted domain entities when provider-specific precomputed values are unavailable.
- Preserve the existing `/api/v1/securities/{symbol}/score` contract while adding gate/profile metadata.
- Add backend endpoints for Piotroski F-Score, Altman Z-Score, cyclicality, and earnings quality.
- Add screener filters for Piotroski range and Altman zone.

## Exclusions

- No React/frontend rendering changes; that belongs to SR2.
- No live FMP API integration for precomputed Piotroski data in this phase.
- No investment advice wording, buy/sell guidance, or personalized recommendations.
- No broad refactor of valuation, ingestion, or portfolio modules.

## Decisions

- SR1 is selected as the first unstarted roadmap phase after the merged VM2 phase. Existing spec folders and recent commits show VM1/VM2 completed, while no `SR1` spec exists.
- Risk services compute from existing persisted fundamentals, ratios, valuations, securities, and dividends.
- Sector profile selection is deterministic from `Security.sector`, dividend availability, and broad sector names.
- The MoS gate caps the final total score only; component scores remain visible and `rawTotalScore` records the uncapped value.
- Screener filtering remains local-database-only and uses the latest persisted risk results.

## Assumptions

- Existing annual fundamentals contain enough revenue, net income, operating cash flow/free cash flow, assets, liabilities, and market-data-adjacent fields for deterministic fallback formulas where the data model exposes them.
- When a formula input is missing, services return a persisted result with an availability/status explanation rather than fabricating values.
- Sector names in provider data are plain strings such as Technology, Financial Services, Utilities, Real Estate, Consumer Cyclical, and Industrials.
- PostgreSQL migrations and H2-backed tests should both accept the new schema.

## Dependencies

- Spring Boot backend, JPA entities, Flyway migrations, repositories, and Maven test suite.
- Existing `Security`, `FundamentalSnapshot`, `RatioSnapshot`, `ValuationResult`, and `ValueScore` persistence.
- Existing screener request/service/controller structure.

## Mission Context

- This phase supports the mission principles of data before opinion, transparency, conservative defaults, and financial resilience before apparent cheapness.
- RULE-09 prevents overvalued companies from ranking highly, keeping screener output aligned with value-investing decision support.

