# PFD1 — Validation

## Automated checks

- Backend compilation and the existing automated test suite pass.
- Static-resource verification confirms `GET /full-demo.html` returns the page successfully.
- No new controller, service, migration, or API-contract change is required for the page to function.
- A repository scan confirms the demo page and specs contain no API key, password, JWT private key, or other credential.

## Manual stakeholder-flow acceptance

1. Open `/full-demo.html`, configure `BASE_URL` if required, and verify the public health panel works before authentication.
2. Log in and verify the authenticated panels become available; verify ADMIN-only panels remain role-gated.
3. As an admin, seed a small ticker set and confirm per-ticker successes and failures are shown independently.
4. Run a screener query, load a preset, sort the results, and select a row; confirm its symbol populates Security Detail.
5. Inspect each Security Detail tab and confirm responses render, raw JSON is available, and valuation-related output includes the MiFID II disclaimer.
6. Add the selected security to a watchlist; confirm it appears, thresholds can be updated, alerts are visible, and removal works.
7. Create and select a portfolio, add/update/remove holdings, then confirm the detail view reflects each change.
8. Run simulation and confirm proposed weights, shares, cost, sector allocation, and average MoS are shown.
9. Run rebalancing and confirm buy/sell recommendations are displayed clearly.
10. Exercise quick analysis and custom DCF valuation, plus cache eviction and job trigger as an admin, to confirm every inherited FD1 panel remains reachable.

## Merge criteria

- `full-demo.html` is standalone, uses only browser-native assets and APIs, and needs no build step or CDN.
- Every endpoint invoked already exists and uses the expected auth token handling.
- All panels have inline status/error feedback and a collapsible raw-JSON view.
- The full stakeholder path from screening through portfolio rebalancing completes against a seeded local environment.
- The worktree contains only intentional PFD1 changes; the pre-existing untracked `spring-boot.log` is left untouched.
