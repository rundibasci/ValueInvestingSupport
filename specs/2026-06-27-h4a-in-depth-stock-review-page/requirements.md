# H4A - In-Depth Stock Review Page

## Purpose

Deliver a dedicated in-depth stock review page that turns a seeded symbol into a complete single-stock research packet. The page should help users read the investment evidence in one focused flow: business context, source coverage, valuation, cash generation, earnings, balance-sheet resilience, dividends, quality/growth, historical graphs, and data-quality caveats.

This phase supports the `Fundamental Analysis -> Intrinsic Value Estimation -> Margin of Safety Calculation -> Recommendation` steps in `specs/mission.md`, while preserving the platform boundary as a decision-support tool rather than investment advice.

## Scope

- Add a standalone protected route at `/securities/:symbol/review`.
- Build a dedicated `SecurityReviewPage` separate from `SecurityDetailPage`; it must not be a modal or hidden tab inside H4.
- Make the page accessible to every authenticated role for symbols in the shared seeded universe, subject to backend authorization and symbol availability.
- Add entry points from all available surfaces at merge time: Screener rows, Security Detail header/actions, Watchlist rows, Portfolio holding rows, and Seed result rows.
- Present a single focused research packet with a compact table-of-contents, jump links, and sticky scroll progress, optimized for reading and comparison rather than tab navigation.
- Show header context: company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations.
- Show source coverage by category: profile, fundamentals, ratios, quote, dividends, valuation, score, and analyst estimates. Coverage labels should support `FMP`, `Yahoo Finance`, `Mixed`, unavailable, and stale states when provided by the backend. If the current H4 endpoints do not expose provider-level coverage metadata, label that as an explicit unavailable data gap rather than implying provider coverage from generic API success.
- Show valuation evidence: DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
- Show cash-generation evidence: FCF TTM/latest annual, FCF history, positive-FCF years, FCF growth, FCF margin when available, and DCF eligibility/data gaps.
- Show earnings evidence: revenue, net income, EPS, earnings history/trend, earnings growth, and quality notes where data is available.
- Show balance-sheet and debt evidence: total debt, cash, net debt, debt-to-equity, current ratio, quick ratio when available, interest coverage when available, and trend context.
- Show historical Recharts graphs for earnings, debt, ROI/ROIC, and ROE, each with source badge, latest data date, unavailable-series handling, responsive desktop/mobile layout, readable axes, tooltips, and accessible summaries.
- Show dividend evidence: dividend yield, dividend history, streak, payout ratio, FCF payout/coverage, dividend CAGR, and dividend sustainability status.
- Show quality and growth evidence: ROIC, ROE, gross/operating/net margins when available, revenue/FCF/EPS CAGR at 3y/5y/10y, and peer/sector context.
- Show risk and data-quality notes: unavailable metrics, stale inputs, provider fallbacks, provider-plan restrictions, source limitations, and model caveats in plain language.
- Provide actions to add to watchlist, add to portfolio (visible but disabled with "coming soon" label until H4B; no portfolio mutation in H4A), open custom DCF controls inline on the review page, refresh/seed symbol if allowed, and return to Screener/Security Detail.
- Reuse the frontend stack from `specs/tech-stack.md`: React 18, TypeScript strict mode, Tailwind CSS, Recharts, TanStack Query, React Router, and existing authenticated API-client patterns.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H4A: In-Depth Stock Review Page, following H4 Security Detail UI and before H5 portfolio-builder work. |
| Primary user value | Give users a complete single-stock research packet without forcing them to move across many tabs. |
| Relationship to H4 | H4A reuses H4 data contracts, API clients, formatters, and chart/error patterns, but renders a separate page and component. |
| Route | `/securities/:symbol/review` is the canonical route. It is protected and symbol-driven. |
| Entry points | All available surfaces are required: Screener rows, Security Detail header/actions, Watchlist rows, Portfolio holding rows, and Seed result rows — for every surface present in the codebase at merge time. |
| Data composition | H4A composes existing H4 endpoints on the frontend. A separate future phase will deliver a dedicated backend `review` endpoint if composition latency or fragility warrants it. |
| Page layout | Compact table-of-contents with jump links and sticky scroll progress, not a fixed linear-only layout. |
| Custom DCF | Open custom DCF controls inline on the review page. |
| Add to portfolio | Show a visible but disabled action with a "coming soon" label. A separate future phase will assess and implement the portfolio-add endpoint integration. |
| Data source posture | Use authenticated application APIs backed by local DB/Redis and backend provider fallback. The frontend must not call FMP/Yahoo directly. |
| Source transparency | Provider coverage, freshness, fallback, unavailable, and stale labels are first-class user-facing evidence when exposed by current contracts. Missing provider metadata is itself shown as an unavailable data gap. |
| Layout | Use a reading-oriented research packet with clearly separated sections rather than another tabbed detail page. |
| Advice boundary | Fair value, margin-of-safety, recommendation, score, and valuation language remains descriptive and non-directive. MiFID II disclaimer is mandatory in valuation/recommendation contexts. |
| Missing data | Missing values are labelled as unavailable or unsupported. The UI must not substitute zero, infer unsupported metrics, or hide data gaps. |

## Context and guardrails

- Mission principle 1 requires data before opinion.
- Mission principle 2 requires valuation transparency, including inputs and assumptions.
- Mission principle 4 requires the system to remain decision support, not regulated investment advice.
- Mission principle 8 requires financial resilience to be shown through leverage, liquidity, interest burden, cash generation, and dividend coverage over time, not reduced to universal pass/fail ratios.
- Mission principle 11 explicitly requires a dedicated in-depth review page that exposes DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, source coverage, freshness, and unavailable-data labels.
- The tech stack defines `/securities/:symbol/review` as the dedicated single-stock research packet route.
- Historical charts must be readable and useful on desktop and narrow viewports. Text must not overlap chart controls, legends, axes, cards, or actions.
- Backend authorization remains authoritative. The frontend must not duplicate ownership rules or leak hidden data from other users.
- Do not expose provider secrets, JWT refresh tokens, raw credentials, stack traces, internal diagnostics, or sensitive user data in source, fixtures, logs, URLs, local storage, or rendered debug output.
- Tests and demos must be deterministic and must not require live FMP/Yahoo calls.

## Resolved feature-spec decisions

1. **Data composition:** H4A composes existing H4 endpoints on the frontend. A separate future phase will implement a dedicated backend `review` endpoint to optimise the data assembly if composition becomes too slow or brittle.
2. **Page layout:** Include a compact table-of-contents with jump links and sticky scroll progress so users can navigate the long research packet quickly.
3. **Custom DCF:** Open custom DCF controls inline on the review page rather than linking back to the H4 Valuation tab or using a separate modal.
4. **Add to portfolio:** Show a visible but disabled action with a "coming soon" label. Do not call portfolio-add mutations in H4A. A separate future phase will assess and implement the portfolio-add endpoint integration.
5. **Entry points:** All available entry points are required for merge — Screener rows, Security Detail header/actions, Watchlist rows, Portfolio holding rows, and Seed result rows — for every surface that exists in the active codebase at merge time.

## Out of scope

- New backend ingestion, provider integrations, valuation formulas, alert rules, portfolio construction logic, or screener-filter changes.
- Replacing H4 Security Detail UI or removing its tabbed workflow.
- Personalised advice, trade execution, brokerage integration, or buy/sell instructions.
- Live provider calls from the frontend or UI behaviour that bypasses authenticated backend APIs.
