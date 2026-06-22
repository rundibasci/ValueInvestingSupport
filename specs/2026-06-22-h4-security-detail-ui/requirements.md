# H4 — Security Detail UI

## Context

H4 turns the placeholder `/securities/:symbol` route reached from the H3 screener into the research workspace for the next stages of the value-investing cycle: fundamental analysis, financial resilience assessment, and intrinsic-value review. It consumes the authenticated Security Detail APIs completed in Group E and presents the data needed to understand a company before any portfolio action.

The frontend foundation is React 18, TypeScript strict mode, TailwindCSS, React Router v6, TanStack Query, and the existing JWT-aware API client. The relevant APIs are `GET /api/v1/securities/{symbol}`, `/financials`, `/ratios`, `/financial-health`, `/valuation`, `/dividends`, `/growth`, `/insiders`, and `/peers`, plus the existing watchlist API. The frontend uses these live application APIs; it does not introduce mock data or direct provider calls.

## Scope

- Replace the protected security-detail placeholder with an accessible, responsive page at `/securities/:symbol`.
- Provide a persistent overview header with company identity, sector, country, market capitalisation, management, current price, data-as-of context, and an Add to Watchlist action.
- Use a summary header and labelled tabs: Overview, Financials, Ratios, Financial Health, Valuation, Dividends, Growth, and Insider. Surface peer comparisons in Overview where the existing endpoint permits.
- Render up to ten years of revenue, net income, and free-cash-flow history in Financials using Recharts, with clear units, dates, missing-data treatment, and an available TTM/quarterly context.
- Render PE, ROIC, ROE, and debt trend charts in Ratios; show unavailable points as unavailable rather than zero.
- Render synchronized Financial Health views for debt (total, short-term, long-term, net debt), revenue, net income, FCF, liquidity, interest coverage, and dividend sustainability. Include metric definitions/data availability and sector or industry context; use trend-oriented caution language, never universal safe/unsafe leverage verdicts.
- Render the Valuation tab with current price, fair-value comparison, margin-of-safety gauge/range, model inputs and assumptions supplied by the API, and a custom DCF form using the existing valuation endpoint contract. Include the MiFID II decision-support disclaimer.
- Render dividend history, streak, payout/coverage data where available, and dividend CAGRs; render Growth CAGRs at 3/5/10 years; render recent insider trades in semantic tables.
- Fetch all data through typed TanStack Query hooks with appropriate loading, empty, unavailable, refetching, and retry states.
- Show recoverable API errors as an on-screen accessible popup/dialog containing the safe error details returned by the application, a retry/dismiss action, and no credentials, tokens, stack traces, or internal diagnostics.

## Decisions

| Topic | Decision |
| --- | --- |
| Information architecture | A compact summary header remains visible above a keyboard-operable tab interface. Tabs isolate dense research topics while retaining the symbol context and Add to Watchlist action. |
| Data source | Use live authenticated application APIs only. The UI makes no direct FMP/Yahoo calls and relies on the backend's cache/DB-first and fallback behaviour. |
| Fetching | Fetch profile/overview eagerly; fetch tab-specific data when its tab first opens, cache it with TanStack Query, and allow explicit retry/refetch after failure. |
| API failure | Display a non-blocking, accessible popup/dialog with endpoint-appropriate, safe server error detail and retry/dismiss controls. Preserve already rendered data and keep unaffected tabs usable. |
| Stale/unavailable data | Clearly show backend stale-data responses and nullable fields as unavailable with any supplied data-as-of date; never substitute zero, fabricated history, or an implied current figure. |
| Financial health language | Explain metrics and trends with sector/industry context; present caution indicators, not universal leverage thresholds or investment recommendations. |
| Charts and tables | Use Recharts for time series and semantic tables for structured records. All charts need textual summaries/tooltips and formatted axes so the data remains understandable. |
| Watchlist | Reuse the authenticated existing watchlist API and provide success, duplicate, failure, and unauthenticated/session-expiry handling; no watchlist-management screen is added. |
| Advice boundary | Include a visible MiFID II decision-support disclaimer wherever fair value, margin of safety, or recommendation-adjacent valuation content appears. |

## Out of Scope

- New backend endpoints, new market-data ingestion, direct provider calls, or changes to valuation calculations.
- Editing financial records, creating trades, personalised advice, portfolio allocation, or watchlist management beyond adding the current security.
- Global search/autocomplete redesign, dashboard work, alerts, or H5+ frontend features.
- Invented health thresholds, missing data, analyst estimates, or investment recommendations.

## Constraints from Mission and Stack

- The page must advance the mission's Fundamental Analysis → Intrinsic Value Estimation → Margin of Safety stages with transparent data before opinion.
- Retain React 18, TypeScript strict mode, TailwindCSS, React Router v6, Recharts, TanStack Query, and the established authenticated API client; do not add a competing UI, state, or charting framework.
- Backend authorization remains authoritative. Respect H2's login/session-expiry flow and never expose credentials, JWTs, refresh tokens, or sensitive diagnostics in the UI, logs, or URLs.
