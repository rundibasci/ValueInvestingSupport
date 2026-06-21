# PFD1 — Implementation Plan

1. Establish the standalone demo shell
   - Review `feature-demo.html` and the completed API contracts through F4.
   - Add `full-demo.html` as a Spring Boot static resource.
   - Create shared request, authentication, authorization visibility, response-status, and raw-JSON-inspector helpers.

2. Carry forward the FD1 workflows
   - Implement auth, logout, health, seed, quick analysis, custom DCF valuation, cache eviction, and job trigger panels.
   - Preserve role-aware access and the MiFID II disclaimer on valuation-related output.

3. Build screening and security investigation
   - Add screener filters, presets, sorting, results, and MoS badges.
   - Link a selected result to the Security Detail panel.
   - Add profile, financials, ratios, valuation, dividends, growth, insiders, peers, and inline watchlist-add interactions.

4. Build watchlist and portfolio management
   - Add watchlist listing, creation, threshold editing, deletion, and active-alert display.
   - Add portfolio list/create/select flows and inline holding CRUD for the selected portfolio.

5. Build portfolio decision-support views
   - Add simulation request and proposed-allocation rendering.
   - Add rebalancing request and buy/sell recommendation rendering.
   - Ensure empty, unauthorized, validation-error, and API-failure states are understandable.

6. Verify and document
   - Run backend tests and manually exercise the stakeholder happy path in a browser.
   - Verify all calls target existing endpoints, no secret is embedded, and no build tooling is required.
   - Update the relevant changelog only if that is part of the implementation handoff process.

