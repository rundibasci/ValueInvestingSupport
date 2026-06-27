# H8 - Seed & Shared Universe UI Validation

## Functional acceptance

- An authenticated investor can open the Seed / Shared Universe UI and seed a CSV ticker list when the backend authorizes the action.
- An authenticated advisor can open the Seed / Shared Universe UI and seed a CSV ticker list when the backend authorizes the action.
- An authenticated admin can seed a CSV ticker list and seed named packs.
- Investor/advisor named-pack access follows backend quota/cost policy and is represented consistently in the UI.
- The UI explains before submission that seeding creates or refreshes platform-wide research-universe data and does not create personal watchlist or portfolio entries.
- CSV preview normalizes tickers to uppercase, removes duplicates, reports invalid entries, and lets the user correct input before submission.
- Partial success is handled: successful symbols remain usable, failed symbols remain visible, and each failed row shows actionable error detail.
- Each result row shows whether data came from FMP, Yahoo Finance, or mixed provider coverage when the API returns that metadata.
- Category-level source coverage appears when available for profile, fundamentals, ratios, quote, dividends, valuation, and score.
- Fallback rows explain why Yahoo Finance was used instead of FMP when the backend returns a fallback reason.
- Source freshness, stale states, unavailable data, and provider-plan limitations are visible and not masked by optimistic values.
- Result rows include company research context when available: ticker, company name, sector, exchange, country, and description/profile excerpt.
- Successful seeded symbols link to Security Detail and In-Depth Review pages.
- The UI provides a handoff to Screener/search after seeding.
- Any surfaced fair value, margin of safety, recommendation, or score includes a MiFID II decision-support disclaimer.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- Focused frontend tests cover:
  - CSV normalization, duplicate removal, invalid-token feedback, and pre-submit preview;
  - investor, advisor, and admin role-sensitive control rendering;
  - CSV seed mutation success, partial success, and failure states;
  - named-pack rendering and submission where allowed;
  - provider badges for FMP, Yahoo Finance, and Mixed;
  - fallback reason and source-freshness rendering;
  - links to Security Detail, In-Depth Review, and Screener/search handoff;
  - disclaimer rendering when decision-support values appear.
- A deterministic browser or integration test verifies the seeding journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin user;
  2. enter a CSV list with duplicates and one invalid token;
  3. verify normalized preview and correction behavior;
  4. submit a mocked partial-success response;
  5. verify successful rows, failed rows, provider badges, freshness, fallback detail, and research handoff links.
- A deterministic admin journey verifies named-pack availability and result rendering.

## Manual review

- Check desktop and narrow layouts for dense but readable tables, stable controls, no text overlap, and clear empty/error states.
- Verify keyboard-only operation, focus order, accessible names, table semantics, badge text, color contrast, and screen-reader-readable fallback/error states.
- Confirm financial wording is descriptive and non-directive.
- Confirm seeded securities are discoverable by all authenticated users through existing search/screener/security-detail flows when backend data is present.
- Confirm watchlist and portfolio handoffs do not imply personal ownership was created by seeding.
- Confirm no provider secrets, JWTs, refresh tokens, credentials, raw provider payloads, or sensitive user data appear in source, fixtures, local storage, logs, or rendered debug output.

## Merge criteria

- All automated checks pass, including production build and deterministic seeding journeys.
- The Seed / Shared Universe UI works in a browser against local deterministic API data for investor, advisor, and admin roles.
- Existing H1-H7 routes, authentication behavior, query-client conventions, and shared UI patterns have no regressions.
- Existing ownership boundaries remain intact: seeded securities are shared reference data, while watchlists and portfolios remain user-owned.
- Feature-spec questions in `requirements.md` are resolved or explicitly deferred before implementation merge.
