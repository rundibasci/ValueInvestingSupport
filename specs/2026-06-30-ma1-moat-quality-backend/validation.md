# MA1 Moat & Quality Backend Validation

## Acceptance Checks

- `GET /api/v1/securities/{symbol}/moat` returns moat classification, ROIC history, consistency, trend, ROIC-WACC spread, reinvestment rate, and stability criteria for seeded symbols.
- `GET /api/v1/securities/{symbol}/capital-allocation` returns shares trend, dilution/buyback percentage, shareholder yield, insider ownership availability, acquisition-spend availability, and allocator classification.
- `GET /api/v1/securities/{symbol}/valuation-bands` returns valuation band data for P/E, P/B, EV/EBITDA, and dividend yield when local history exists.
- `GET /api/v1/securities/{symbol}/review` includes the new MA1 sections without breaking existing review fields.
- Screener requests can filter by moat strength and shares outstanding trend from persisted latest results.
- Unknown symbols return the existing symbol-not-found behavior.
- Missing history is represented as insufficient data rather than fabricated results.

## Test Strategy

- Unit tests:
  - ROIC consistency classification: wide, narrow, none, insufficient.
  - Shares outstanding trend: net buyback, stable, net diluter.
  - Valuation bands percentile classification: cheap, normal, expensive.
  - Stability criteria pass/fail/insufficient data.
- Existing API/review/screener tests should continue passing.

## Commands

- `cd backend; .\mvnw.cmd test`
- `git diff --check`

## Manual QA

- Inspect JSON shapes for the three new endpoints and the review response when tests or local seeded data make this practical.
- Confirm no provider API keys or secret values are introduced.

## Merge Readiness

- Generated spec files are present and non-empty.
- Validation commands pass.
- Changelog is updated during merge workflow.
- Obsidian activity note is written.

## Known Risks

- Current persisted data does not store insider ownership percentage or acquisition spending explicitly. The first version must label those fields unavailable rather than inferring unreliable values.
- Historical data availability varies by seeded source; some symbols may produce partial valuation bands or stability results.
