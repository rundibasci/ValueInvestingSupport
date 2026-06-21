# PFD1 — Complete Feature Demo Page

## Context

PFD1 is the next roadmap phase after F4. It makes the production value-investing workflow demonstrable to stakeholders in a browser before the React frontend begins. The page must expose the capabilities delivered through F4 while preserving the existing FD1 demo capabilities.

It supports the product mission by making the value-investing cycle tangible: screen candidates, inspect their fundamentals and valuation, place them on a watchlist, construct a portfolio, simulate a target allocation, and review rebalancing proposals. It remains decision support: valuation- and score-related output must retain the MiFID II disclaimer.

## Scope

- Add a standalone `backend/src/main/resources/static/full-demo.html`.
- Serve it directly from Spring Boot, with no backend endpoint additions.
- Use pure HTML5, CSS, and vanilla JavaScript only: no npm, bundler, CDN, or framework.
- Keep a configurable `BASE_URL` constant at the top of the script.
- Include all existing FD1 workflows: authentication, health, admin seed, quick analysis, DCF custom valuation, cache eviction, and job trigger.
- Add the PFD1 workflows: screener, security detail, watchlist, portfolios and holdings, portfolio simulation, and portfolio rebalancing.
- Provide a collapsible raw-JSON inspector for every panel.
- Design the happy path around stakeholder discovery: authenticate, seed data, screen candidates, investigate a selected security, add it to a watchlist, build a portfolio, simulate it, and inspect the rebalancing recommendation.

## Decisions

- Deliver a new standalone `full-demo.html`; do not modify or replace `feature-demo.html`.
- Reuse the FD1 interaction conventions where practical so the two demo pages feel familiar.
- Keep JWT in JavaScript memory and dynamically show/hide authenticated and ADMIN-only controls, consistent with FD1.
- Every request must surface status and useful error details inline; partial failures, especially seed results, must not hide successful results.
- Screener result rows select and populate the Security Detail symbol, linking the primary stakeholder journey without a separate router.
- Preserve transparent data presentation: responses and valuation inputs remain inspectable through raw JSON.
- Do not include credentials, API keys, or other secrets in the page or specification.

## Out of Scope

- React/Vite frontend work, routing, charts, and production-grade visual design.
- New API endpoints or API contract changes.
- Alert-engine capabilities beyond the existing watchlist-alert read endpoint.
- Brokerage, order execution, or investment advice.

