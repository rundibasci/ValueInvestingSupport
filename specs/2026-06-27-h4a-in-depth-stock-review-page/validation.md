# H4A - In-Depth Stock Review Page Validation

## Functional acceptance

- Authenticated users can open `/securities/:symbol/review` for a symbol in the shared seeded universe.
- Signed-out users are redirected through the existing protected-route login flow and return to the requested review page after authentication.
- The page renders as a standalone `SecurityReviewPage`, not a modal, hidden tab, or conditional state inside `SecurityDetailPage`.
- The page can be reached from all available surfaces: Screener rows, Security Detail header/actions, Watchlist rows, Portfolio holding rows, and Seed result rows.
- The header shows company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations when available.
- Source coverage shows `FMP`, `Yahoo Finance`, `Mixed`, unavailable, and stale states by category when provided by the backend.
- If the current H4 endpoints do not expose provider/fallback/freshness metadata, the page explicitly labels provider-level source coverage as unavailable instead of substituting a generic application-API label.
- The valuation section shows DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
- Cash-generation, earnings, balance-sheet/debt, dividend, quality/growth, and risk/data-quality sections render available values and clear unavailable labels for missing data.
- The page includes historical graphs for earnings history, debt history, ROI/ROIC history, and ROE history using Recharts.
- Return-on-invested-capital charts are labelled as `ROIC` when the API supplies ROIC rather than generic ROI.
- Charts show source badge, latest data date, unavailable-series handling, responsive desktop/mobile layout, readable axes/tooltips, and accessible textual summaries.
- The page includes a compact table-of-contents with jump links and sticky scroll progress.
- Custom DCF controls open inline on the review page.
- Add to watchlist action is functional; add to portfolio is visible but disabled with a "coming soon" label and does not call portfolio mutations in H4A.
- Actions for inline custom DCF, return to Screener/Security Detail, and refresh/seed are available when permitted.
- Loading, empty, partial-data, stale-data, unavailable, API-error, unauthorized, and expired-session states are clear and recoverable.
- The UI never substitutes missing values with zero, fabricates unsupported metrics, or hides data-quality caveats.

## Automated checks

- Frontend linting, TypeScript type checking, unit/component tests, and production build pass.
- Focused frontend tests cover:
  - protected route behaviour and symbol normalization;
  - query composition and caching across the reused H4 endpoint contracts;
  - header rendering with provider, freshness, and data-source limitation labels when present;
  - source coverage by category, including explicit unavailable labels when provider metadata is absent from current contracts;
  - valuation, cash-generation, earnings, balance-sheet/debt, dividend, quality/growth, and risk/data-quality sections;
  - historical chart transformations for earnings, debt, ROI/ROIC, and ROE;
  - unavailable, stale, partial-data, and API-error states;
  - MiFID II disclaimer rendering where valuation, recommendation, margin-of-safety, or score values appear;
  - table-of-contents jump links and sticky scroll progress;
  - inline custom DCF controls;
  - disabled add-to-portfolio with "coming soon" label and no portfolio-add mutation;
  - action links to Screener, Security Detail, Watchlist rows/cards, Portfolio holding rows/cards, and available seed flows.
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
- H4A does not ship functional portfolio-add behavior from the review page; that remains reserved for H4B.
- Required entry points are present from every implemented surface at merge time: Screener, Security Detail, Watchlist, Portfolio, and Seed results.
- All feature-spec decisions in `requirements.md` are resolved.
