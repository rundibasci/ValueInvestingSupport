# MA1 Moat & Quality Backend Requirements

## Scope

Implement the first backend phase of Group MA: Moat & Business Quality Analysis. The phase adds persisted business-quality analytics and API surfaces that help distinguish durable businesses from merely cheap securities.

The selected roadmap item is the earliest unstarted phase after the existing SR2 spec: `Phase MA1: Moat & Quality Backend`.

## Included

- Add persisted moat assessment results based on historical ROIC versus estimated WACC.
- Add persisted capital allocation results based on shares outstanding trend, shareholder yield, insider ownership when available, and acquisition-spend proxy availability.
- Add persisted historical valuation band results for P/E, P/B, EV/EBITDA, and dividend yield.
- Add persisted long-term stability results with individual Graham stability criteria.
- Add authenticated endpoints:
  - `GET /api/v1/securities/{symbol}/moat`
  - `GET /api/v1/securities/{symbol}/capital-allocation`
  - `GET /api/v1/securities/{symbol}/valuation-bands`
- Include moat, capital allocation, valuation bands, and stability data in `GET /api/v1/securities/{symbol}/review`.
- Add moat strength and shares outstanding trend filters to the screener API.
- Add focused unit tests for classification and calculation rules.

## Excluded

- Frontend rendering for moat, capital allocation, valuation bands, or stability. That belongs to MA2.
- New external provider calls for insider ownership or acquisition spending. Current persisted provider data is used where available.
- Investment advice or buy/sell language.
- Broad ingestion changes beyond data fields already persisted by previous phases.

## Decisions

- Moat strength is calculated from annual ratio snapshots, using `RatioSnapshot.roic` and the latest persisted WACC when available. If WACC is unavailable, use the configured conservative WACC default.
- ROIC values in existing ratio snapshots may be decimals or percentages depending on source history; services normalize values above 1.0 to decimal form.
- Capital allocation uses annual fundamental snapshots for shares outstanding, dividend records for dividend yield continuity, price quotes for current quote context, and insider trades only for activity availability. Insider ownership is returned as unavailable when no ownership percentage is persisted.
- Valuation bands are computed from annual ratio snapshots using existing P/E, P/B, EV/EBITDA, and dividend yield fields. Percentiles are deterministic nearest-rank values.
- Stability results are returned together with moat data and inside the review response so MA2 can render one backend-backed scorecard.

## Assumptions

- Existing historical ratio and fundamental snapshots are sufficient to compute the first backend version without adding market-data endpoints.
- FMP/Yahoo fallback remains isolated in the market-data layer; MA1 operates on local persisted snapshots only.
- Screener filters can join latest moat results similarly to latest score and risk result joins.
- Existing Flyway H2 mirror migrations must be updated alongside PostgreSQL migrations because tests use H2.

## Dependencies

- Existing `security`, `fundamental_snapshot`, `ratio_snapshot`, `valuation_result`, `wacc_result`, `price_quote`, and screener infrastructure.
- Existing Spring Security JWT protection for `/api/**`.
- Existing review endpoint DTO composition.

## Context

Mission principles require data before opinion, transparency, conservative defaults, explainable missing data, and decision-support boundaries. MA1 supports those principles by making business durability, capital allocation, valuation history, and stability explicit backend outputs.
