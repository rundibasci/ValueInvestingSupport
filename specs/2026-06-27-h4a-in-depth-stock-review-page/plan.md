# H4A - In-Depth Stock Review Page Plan

1. **Scope alignment and contract review**
   - Review H4 Security Detail UI contracts, shared API-client/query-hook conventions, route protection, chart components, formatter utilities, and error-state components.
   - Compose the review packet from existing H4 endpoint calls on the frontend (profile, financials, ratios, financial health, valuation, dividends, growth, peers, and score). A separate future phase will deliver a dedicated backend `review` endpoint if composition latency or fragility warrants it.
   - Apply resolved feature-spec decisions exactly: table-of-contents with jump links and sticky scroll progress; inline custom DCF controls; disabled add-to-portfolio with "coming soon" label; all available entry points required at merge time.
   - Keep H4A inside its phase boundary. Do not implement a functional add-to-portfolio mutation on the review page in this phase; that belongs to H4B even if the portfolio API and frontend client already exist.
   - Add or update typed models, query keys, reusable selectors, route wiring, and protected navigation helpers for `/securities/:symbol/review`.

2. **Review page shell and source context**
   - Build `SecurityReviewPage` as a standalone protected route, separate from `SecurityDetailPage` and not implemented as a modal or hidden tab.
   - Build a compact table-of-contents sidebar/header with jump links to each research section plus a sticky scroll progress indicator that remains visible while reading the long packet.
   - Add entry links from all available surfaces at merge time:
     - Screener rows/results.
     - Security Detail header/actions.
     - Watchlist security cards and active-alert cards.
     - Portfolio holding rows/cards.
     - Admin Seed successful result rows.
   - Build the review header with company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations.
   - Build a source coverage section for profile, fundamentals, ratios, quote, dividends, valuation, score, and analyst estimates.
   - When current H4 endpoint contracts expose provider/fallback/freshness metadata, show `FMP`, `Yahoo Finance`, `Mixed`, unavailable, and stale states by category.
   - When current H4 endpoint contracts do not expose provider/fallback/freshness metadata, label the provider-level state as an explicit unavailable data gap and avoid substituting generic labels such as "Application API" as if they were provider coverage.

3. **Single-stock research packet sections**
   - Build the Valuation section with DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
   - Build the Cash Generation section with FCF TTM/latest annual, FCF history, positive-FCF years, FCF growth, FCF margin when available, and DCF eligibility/data gaps.
   - Build the Earnings section with revenue, net income, EPS, earnings history/trend, earnings growth, and quality notes where available.
   - Build the Balance Sheet and Debt section with total debt, cash, net debt, debt-to-equity, current ratio, quick ratio when available, interest coverage when available, and trend context.
   - Build the Dividend section with dividend yield, dividend history, streak, payout ratio, FCF payout/coverage, dividend CAGR, and sustainability status.
   - Build the Quality and Growth section with ROIC, ROE, margins when available, revenue/FCF/EPS CAGR at 3y/5y/10y, and peer/sector context.
   - Build the Risk and Data Quality section with unavailable metrics, stale inputs, provider fallbacks, provider-plan restrictions, and model caveats in plain language.

4. **Historical graphs and actions**
   - Add Recharts historical graphs for earnings history, debt history, ROI/ROIC history, and ROE history with source badges when available, latest data dates when available, unavailable-series handling, responsive layout, readable axes, and accessible summaries.
   - Label return-on-invested-capital charts as `ROIC` when the API supplies ROIC rather than generic ROI.
   - Add actions to add the symbol to a watchlist, add it to a portfolio (visible but disabled with "coming soon" label and no mutation), open inline custom DCF controls, refresh/seed when allowed, and return to Screener or Security Detail.
   - Preserve existing authenticated API boundaries and avoid direct FMP/Yahoo calls from the frontend.

5. **States, quality, and merge readiness**
   - Implement loading, empty, partial-data, stale-data, unavailable, API-error, unauthorized, and expired-session states without hiding already available data.
   - Add focused frontend tests for route protection, section rendering, chart data handling, source/freshness labels or explicit provider-metadata gaps, unavailable states, action links, disabled portfolio-add behavior, custom DCF access, and disclaimer rendering.
   - Add deterministic integration or browser-level coverage for an authenticated non-admin user opening `/securities/:symbol/review` for a seeded symbol.
   - Run linting, TypeScript checks, tests, production build, and responsive/manual accessibility review.
   - Confirm all resolved feature-spec decisions are reflected in the implementation and keep scope limited to H4A.
