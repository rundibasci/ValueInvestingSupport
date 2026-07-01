# Requirements - Phase PI2: Portfolio Intelligence Frontend

## Scope

Add the frontend surface for the portfolio intelligence backend delivered in PI1. The selected roadmap phase is the first phase after `PI1` without a matching spec directory, and it appears before `PW1` in `specs/roadmap.md`.

## In Scope

- Extend the existing portfolio page with a portfolio analytics dashboard.
- Display weighted-average metrics: margin of safety, P/E, dividend yield, value score, and Piotroski F-Score.
- Display sector allocation with concentration flags from the backend.
- Display holding concentration states for immaterial, normal, and concentrated positions.
- Display moat profile and quality distribution summaries.
- Add liquidity indicators to holdings using PI1 liquidity classification and days-to-liquidate values.
- Display benchmark comparison for portfolio versus default benchmark characteristics.
- Enhance rebalance UI with urgency, estimated transaction cost, total estimated transaction cost, holding period, and position-size warnings.
- Preserve the decision-support boundary in UI copy.

## Exclusions

- No backend endpoint changes unless required by a compile-time contract mismatch.
- No new portfolio analytics history page.
- No tax-lot accounting, brokerage integration, or order recommendations.
- No full React route redesign or unrelated visual polish.
- No new charting dependency beyond existing Recharts.

## Decisions

- The existing portfolio route remains the PI2 surface because users already manage holdings, simulations, and rebalancing there.
- The UI consumes PI1 fields as returned; it does not recompute analytics client-side beyond formatting and simple chart mapping.
- Missing analytics data renders as unavailable values and backend warnings, not as failed page state.
- Liquidity and concentration styling is informational: green/yellow/red status chips describe diagnostics and do not tell the user to buy or sell.
- Benchmark comparison is characteristic-based only and does not display return tracking.

## Assumptions

- PI1 endpoint `GET /api/v1/portfolios/{id}/analytics` is available in non-demo profiles.
- The frontend currently has Recharts installed and should reuse it.
- Analytics values are numeric percentages where backend DTO names use `Percent`, and scalar ratios otherwise.
- The default benchmark may be unavailable locally; the UI should show `availabilityStatus` and still render portfolio-side metrics.
- Current untracked server log files are runtime artifacts and are unrelated to PI2.

## Dependencies

- PI1 portfolio analytics endpoint and rebalance response fields.
- Existing `portfolioApi`, React Query, TailwindCSS, and Recharts frontend stack.
- Existing portfolio route and authenticated API client.

