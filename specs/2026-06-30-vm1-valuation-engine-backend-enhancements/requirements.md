# VM1 - Valuation Engine Backend Enhancements Requirements

## Roadmap Selection

The next unstarted roadmap phase is `VM1: Valuation Engine Backend Enhancements`. Earlier phases have matching spec folders through `I2`, and `J1` is explicitly marked complete in the roadmap, so VM1 is the earliest valid phase.

## Scope

- Add backend valuation depth for WACC, DCF sensitivity, EPV, owner earnings, Graham criteria, and composite-weight handling.
- Persist VM1 valuation assumptions/results with `ValuationResult` or dedicated trace tables where a one-to-many structure is needed.
- Keep the work backend-only; VM2 owns the React display and interactive override UI.
- Extend existing valuation services, domain entities, migrations, and unit tests without changing the data-source abstraction.

## Exclusions

- No frontend UI changes.
- No live Treasury-rate or sector-median provider integration in this phase.
- No commercial investment advice language.
- No secrets or provider credentials.

## Decisions

- Use deterministic configurable defaults for risk-free rate, equity risk premium, sector fallback WACC, maintenance-capex ratio, and default composite weights.
- Store DCF terminal value percentage and high-terminal-dependence flags directly on `valuation_result`.
- Store WACC and Graham checklist details in separate tables linked to `valuation_result`, because both are assumption/evidence records rather than core fair-value columns.
- Persist EPV and owner earnings directly on `valuation_result`.
- Preserve existing DCF/Graham/DDM behavior for callers while enriching `ValuationOutcome`.

## Assumptions

- Current local date is 2026-06-30.
- VM1 may use repository data already available in `FundamentalSnapshot`, `RatioSnapshot`, `DividendRecord`, `Security`, and `PriceQuote`; missing provider fields are represented as insufficient data or conservative fallback values.
- User-specific composite-weight preferences are persisted as a backend model and defaulted by configuration, but UI management is deferred to VM2.
- Sector-median WACC fallback is a single configurable default until sector-level provider data exists.

## Dependencies

- Java 21, Spring Boot, Spring Data JPA, Flyway, Maven.
- Existing valuation calculators and repositories.
- Existing financial snapshot and dividend history persistence.

## Decision-Support Boundary

All outputs are calculation support. They must expose assumptions, fallback state, and insufficient data rather than making buy/sell instructions.
