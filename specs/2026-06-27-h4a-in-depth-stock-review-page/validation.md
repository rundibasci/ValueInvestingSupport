# H4A - In-Depth Stock Review Page Validation

## Functional acceptance

- Authenticated users can open `/securities/:symbol/review` for a symbol in the shared seeded universe.
- Signed-out users are redirected through the existing protected-route login flow and return to the requested review page after authentication.
- The page renders as a standalone `SecurityReviewPage`, not a modal, hidden tab, or conditional state inside `SecurityDetailPage`.
- The page can be reached from Screener rows and Security Detail header/actions; Watchlist, Portfolio, and Seed result entry points are present when those surfaces exist in the active codebase.
- The header shows company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations when available.
- Source coverage shows `FMP`, `Yahoo Finance`, `Mixed`, unavailable, and stale states by category when provided by the backend.
- The valuation section shows DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
- Cash-generation, earnings, balance-sheet/debt, dividend, quality/growth, and risk/data-quality sections render available values and clear unavailable labels for missing data.
- The page includes historical graphs for earnings history, debt history, ROI/ROIC history, and ROE history using Recharts.
- Return-on-invested-capital charts are labelled as `ROIC` when the API supplies ROIC rather than generic ROI.
- Charts show source badge, latest data date, unavailable-series handling, responsive desktop/mobile layout, readable axes/tooltips, and accessible textual summaries.
- Actions are available for add to watchlist, custom DCF access, return to Screener/Security Detail, and other phase-dependent actions only when their API/UI contracts exist.
- Loading, empty, partial-data, stale-data, unavailable, API-error, unauthorized, and expired-session states are clear and recoverable.
- The UI never substitutes missing values with zero, fabricates unsupported metrics, or hides data-quality caveats.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- Focused frontend tests cover:
  - protected route behaviour and symbol normalization;
  - query composition and caching across the reused H4 endpoint contracts;
  - header rendering with provider, freshness, and data-source limitation labels;
  - source coverage by category;
  - valuation, cash-generation, earnings, balance-sheet/debt, dividend, quality/growth, and risk/data-quality sections;
  - historical chart transformations for earnings, debt, ROI/ROIC, and ROE;
  - unavailable, stale, partial-data, and API-error states;
  - MiFID II disclaimer rendering where valuation, recommendation, margin-of-safety, or score values appear;
  - action links to Screener, Security Detail, custom DCF controls, and available watchlist/portfolio/seed flows.
- A deterministic integration or browser-level test verifies the H4A journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin investor;
  2. open `/securities/AAPL/review` or another seeded test symbol;
  3. verify header/source coverage and all research packet sections;
  4. verify historical charts render non-empty data and unavailable labels where seeded data is missing;
  5. navigate back to Security Detail and Screener;
  6. verify protected-route behaviour after session expiry.

## Manual review

- Check desktop and narrow layouts for reading flow, stable section dimensions, readable charts, accessible tables, and no text overlap.
- Verify keyboard-only operation, focus order, skip/jump links if present, action controls, chart summaries, error handling, and visible focus states.
- Verify color contrast, badge text, tooltip readability, and screen-reader-readable status/source labels.
- Confirm financial wording is descriptive and non-directive.
- Confirm financial-health and debt language is trend/context based and avoids universal safe/unsafe leverage claims.
- Confirm no provider secrets, JWT refresh tokens, raw credentials, stack traces, internal diagnostics, or sensitive user data appear in source, fixtures, local storage, logs, URLs, or rendered debug output.

## Merge criteria

- All automated checks pass, including production build and deterministic authenticated review-page journey.
- `/securities/:symbol/review` works in a browser against deterministic local data and existing authenticated API contracts.
- Existing H4 Security Detail UI, H3 Screener UI, authentication behaviour, query-client conventions, chart patterns, and shared UI components have no regressions.
- The page displays DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, source coverage, freshness, and unavailable-data labels.
- Feature-spec questions in `requirements.md` are resolved or explicitly deferred before implementation merge.
