# H4 — Security Detail UI Plan

## 1. Confirm and model the detail contracts

1.1 Inspect the existing authenticated Security Detail and watchlist endpoint DTOs, including nullable fields, stale-data responses, current DCF request/response contract, and safe error payloads.

1.2 Add strict frontend types, formatters, and API-client functions for profile, financials, ratios, financial health, valuation, dividends, growth, insiders, peers, and watchlist addition.

1.3 Create TanStack Query hooks with profile eager-loading, lazy per-tab queries, stable symbol-based query keys, and consistent session-expiry handling.

## 2. Build the page shell and navigation

2.1 Replace the `/securities/:symbol` placeholder with a protected responsive research page and validate/normalise the route symbol.

2.2 Build the company summary header with profile context, price/data date, peer summary, and Add to Watchlist action.

2.3 Implement an accessible keyboard-operable tab interface for Overview, Financials, Ratios, Financial Health, Valuation, Dividends, Growth, and Insider; preserve tab state sensibly during refetches.

2.4 Implement a reusable safe error popup/dialog and in-context loading, empty, stale, and unavailable-data components.

## 3. Implement fundamental and resilience research tabs

3.1 Build Overview content for company profile, management, business context, key figures, and available peers.

3.2 Build Financials charts for annual revenue, net income, and FCF, with quarterly/TTM supporting context and transparent units/date labels.

3.3 Build Ratios charts for PE, ROIC, ROE, and debt trends.

3.4 Build Financial Health synchronized charts and supporting metric/context panels for debt, earnings/cash generation, liquidity, interest coverage, and dividend sustainability without universal pass/fail claims.

## 4. Implement valuation and shareholder-data tabs

4.1 Build the Valuation tab: price/fair-value comparison, MoS gauge and range, model input transparency, custom DCF form, response handling, and the MiFID II disclaimer.

4.2 Build the Dividends tab with history chart, streak, CAGR, payout, and coverage presentation that faithfully handles missing data.

4.3 Build the Growth and Insider tabs with 3/5/10-year CAGR tables and accessible recent-trade tables.

4.4 Wire Add to Watchlist to the existing API and implement successful, duplicate, failure, and session-expiry feedback.

## 5. Verify and prepare merge evidence

5.1 Add unit/component tests for typed requests, query states, tabs, charts/tables, nullable data, DCF form, watchlist addition, error popup details/retry/dismiss, and authentication expiry.

5.2 Run strict TypeScript checking, frontend tests, production build, and relevant existing backend Security Detail and watchlist tests.

5.3 Perform browser checks for each tab, chart legibility, responsive layout, keyboard/screen-reader flow, live error presentation, and all stated advice-boundary language.
