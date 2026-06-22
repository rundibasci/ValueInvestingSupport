# H5 — Portfolio Builder UI

## Purpose

Deliver the authenticated React interface for the portfolio part of the value-investing cycle: construct a model portfolio, inspect and edit its holdings, and create/review rebalance proposals. This turns the existing Group F APIs into a practical workflow for ADVISOR and INVESTOR users.

## Scope

- Add protected portfolio routes and navigation to create, list, view, update, and delete portfolios using the existing portfolio CRUD APIs.
- Build the portfolio simulation flow using budget, risk profile, yield target, and the full set of allocation constraints exposed by the API.
- Present a proposed allocation table, allow users to edit allocation weights, and show immediate validation for total allocation plus sector, stock, and country concentration constraints.
- Visualize proposed and saved sector allocation with a Recharts donut chart.
- Save a valid proposal as a portfolio, then allow holdings to be viewed, added, edited, and removed.
- Add a rebalancing view that requests, displays, and refreshes the existing rebalancing proposal, including current versus target weights and recommended trades.
- Apply the MiFID II decision-support disclaimer wherever allocations, valuation-derived figures, or recommendations are displayed.

## Decisions

| Topic | Decision |
|---|---|
| Feature breadth | H5 includes all portfolio functions: builder/simulation, CRUD and holdings, and rebalancing. |
| Constraints | Expose allocation constraints rather than hiding them behind presets. Budget, risk profile, yield target, holding count, minimum margin of safety, and sector/stock/country caps must be represented when supported by the API. |
| Data flow | Reuse the Group F REST endpoints; H5 does not add backend endpoints or change allocation algorithms. |
| Frontend stack | React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Hook Form, React Router, and Recharts, as specified in `specs/tech-stack.md`. |
| Access | Require the existing authenticated session and respect backend ownership/role responses; do not duplicate authorization rules in the UI. |
| Validation | Merge requires a browser-level end-to-end test in addition to focused frontend tests and a production build. |

## Context and guardrails

- This phase implements roadmap Phase H5 and consumes the portfolio capabilities delivered in F2–F4.
- The product is decision support, not investment advice. Preserve transparent inputs, calculated outputs, and the required disclaimer.
- Financial resilience and diversification constraints must be visible to the user; the UI must not represent a simulation as a guaranteed outcome.
- Keep server state in TanStack Query, forms in React Hook Form, and API interactions in the existing frontend client layer. Do not introduce a second state-management or charting library.
- Errors, empty portfolios, invalid inputs, and unauthorized/not-found backend responses need clear, recoverable UI states.

## Out of scope

- New portfolio algorithms, new backend endpoints, brokerage/order execution, portfolio accounting or P&L tracking.
- Watchlist and alert user interfaces (H6) and dashboard work (H7).
