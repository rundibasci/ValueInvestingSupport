# H4A - In-Depth Stock Review Page Plan

1. **Scope alignment and contract review**
   - Review H4 Security Detail UI contracts, shared API-client/query-hook conventions, route protection, chart components, formatter utilities, and error-state components.
   - Confirm whether H4A reuses existing H4 endpoint calls or needs a thin frontend composition layer to assemble the review packet from profile, financials, ratios, financial health, valuation, dividends, growth, peers, and score data.
   - Confirm feature-spec decisions for page layout, default section order, custom DCF access, add-to-portfolio behaviour, refresh/seed visibility, and entry-point timing from future H5/H8 routes.
   - Add or update typed models, query keys, reusable selectors, route wiring, and protected navigation helpers for `/securities/:symbol/review`.

2. **Review page shell and source context**
   - Build `SecurityReviewPage` as a standalone protected route, separate from `SecurityDetailPage` and not implemented as a modal or hidden tab.
   - Add entry links from the current feasible surfaces, at minimum Screener rows and Security Detail header/actions, while leaving route-safe hooks for Watchlist, Portfolio, and Seed result rows as those phases become available.
   - Build the review header with company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations.
   - Build a source coverage section showing `FMP`, `Yahoo Finance`, or `Mixed` coverage by category: profile, fundamentals, ratios, quote, dividends, valuation, score, and analyst estimates.

3. **Single-stock research packet sections**
   - Build the Valuation section with DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
   - Build the Cash Generation section with FCF TTM/latest annual, FCF history, positive-FCF years, FCF growth, FCF margin when available, and DCF eligibility/data gaps.
   - Build the Earnings section with revenue, net income, EPS, earnings history/trend, earnings growth, and quality notes where available.
   - Build the Balance Sheet and Debt section with total debt, cash, net debt, debt-to-equity, current ratio, quick ratio when available, interest coverage when available, and trend context.
   - Build the Dividend section with dividend yield, dividend history, streak, payout ratio, FCF payout/coverage, dividend CAGR, and sustainability status.
   - Build the Quality and Growth section with ROIC, ROE, margins when available, revenue/FCF/EPS CAGR at 3y/5y/10y, and peer/sector context.
   - Build the Risk and Data Quality section with unavailable metrics, stale inputs, provider fallbacks, provider-plan restrictions, and model caveats in plain language.

4. **Historical graphs and actions**
   - Add Recharts historical graphs for earnings history, debt history, ROI/ROIC history, and ROE history with source badges, latest data dates, unavailable-series handling, responsive layout, readable axes, and accessible summaries.
   - Label return-on-invested-capital charts as `ROIC` when the API supplies ROIC rather than generic ROI.
   - Add actions to add the symbol to a watchlist, add it to a portfolio when the portfolio API/UI contract exists, open custom DCF controls, refresh/seed when allowed, and return to Screener or Security Detail.
   - Preserve existing authenticated API boundaries and avoid direct FMP/Yahoo calls from the frontend.

5. **States, quality, and merge readiness**
   - Implement loading, empty, partial-data, stale-data, unavailable, API-error, unauthorized, and expired-session states without hiding already available data.
   - Add focused frontend tests for route protection, section rendering, chart data handling, source/freshness labels, unavailable states, action links, custom DCF access, and disclaimer rendering.
   - Add deterministic integration or browser-level coverage for an authenticated non-admin user opening `/securities/:symbol/review` for a seeded symbol.
   - Run linting, TypeScript checks, tests, production build, and responsive/manual accessibility review.
   - Resolve feature-spec questions, update the spec with final decisions, and keep implementation limited to H4A.
