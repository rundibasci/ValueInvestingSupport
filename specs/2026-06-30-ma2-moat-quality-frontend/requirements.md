# MA2 Moat & Quality Frontend Requirements

## Scope

Implement the frontend phase of Group MA: Moat & Business Quality Analysis. The phase makes MA1 backend analytics visible in the React application so users can assess business durability, management capital allocation, historical valuation context, and long-term stability from the review, screener, and comparison workflows.

The selected roadmap item is the first unstarted phase after the existing `2026-06-30-ma1-moat-quality-backend` spec: `Phase MA2: Moat & Quality Frontend`. Roadmap notes explicitly defer J2/J3 until after Group MA, so MA2 is the earliest valid next phase.

## Included

- Review page moat assessment card:
  - moat classification badge: wide, narrow, none, or unavailable
  - 10-year ROIC chart overlaid with estimated WACC line
  - ROIC consistency percentage, trend direction, average ROIC spread, and reinvestment rate
  - one-line factual explanation of the classification
- Review page capital allocation card:
  - normalized shares outstanding chart where year one equals 100
  - net buyback/dilution percentage
  - total shareholder yield
  - insider ownership availability or value when provided
  - capital allocator classification badge
- Review page valuation band section:
  - P/E and EV/EBITDA band charts showing 25th to 75th percentile range, median, and current value
  - current valuation position label: historically cheap, normal, expensive, or unavailable
- Review page stability scorecard:
  - count of passed Graham stability criteria
  - individual pass/fail/unavailable rows with actual values where available
  - link or route affordance to the existing Graham criteria checklist if present
- Screener result columns:
  - moat strength
  - shares outstanding trend
- Cross-symbol comparison additions where a comparison surface exists:
  - moat strength
  - capital allocator type
  - historical valuation position

## Excluded

- New backend calculations or endpoint behavior unless the current frontend cannot consume already-implemented MA1 data.
- New provider calls, seed workflows, or ingestion changes.
- Investment advice, buy/sell language, or recommendations beyond existing decision-support labels.
- A new standalone comparison feature if no comparison surface exists; in that case the implementation documents the deferral.

## Decisions

- Use existing Recharts dependency for ROIC/WACC, shares outstanding, and valuation band charts.
- Render partial or missing history explicitly as unavailable, insufficient history, or provider-limited based on backend availability fields rather than hiding the section.
- Keep business-quality UI inside the existing security review page instead of adding new routes.
- Use compact badges and dense cards consistent with the existing analytical UI.
- Screener columns are display-only in MA2 unless existing filter controls already support the MA1 backend filters.

## Assumptions

- MA1 backend endpoints and review response fields are available or represented in existing frontend mock/demo data.
- Existing frontend styles and chart patterns are sufficient; no new UI package is required.
- If the backend returns enum names in uppercase snake case, frontend display helpers will normalize them for labels.
- Cross-symbol comparison may not exist as a dedicated implemented page yet; MA2 will extend it only if present.
- Implementation check: no dedicated cross-symbol comparison surface exists in the current React routes. MA2 therefore defers comparison additions rather than creating a new comparison workflow outside the roadmap scope.

## Dependencies

- `frontend` React 18 + TypeScript + Vite + TailwindCSS.
- Recharts already installed in the frontend.
- MA1 backend review/screener contract for moat, capital allocation, valuation bands, and stability.
- Mission principles: transparency, conservative defaults, explainable missing data, and decision-support boundary.

## Context

Group MA addresses the roadmap gap between apparent cheapness and business quality. MA2 must make durable-return evidence visible: whether ROIC consistently exceeds cost of capital, whether management diluted or retired shares, whether current valuation is unusual versus history, and whether long-term stability criteria are individually met.
