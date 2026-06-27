# H4A - In-Depth Stock Review Page

## Purpose

Deliver a dedicated in-depth stock review page that turns a seeded symbol into a complete single-stock research packet. The page should help users read the investment evidence in one focused flow: business context, source coverage, valuation, cash generation, earnings, balance-sheet resilience, dividends, quality/growth, historical graphs, and data-quality caveats.

This phase supports the `Fundamental Analysis -> Intrinsic Value Estimation -> Margin of Safety Calculation -> Recommendation` steps in `specs/mission.md`, while preserving the platform boundary as a decision-support tool rather than investment advice.

## Scope

- Add a standalone protected route at `/securities/:symbol/review`.
- Build a dedicated `SecurityReviewPage` separate from `SecurityDetailPage`; it must not be a modal or hidden tab inside H4.
- Make the page accessible to every authenticated role for symbols in the shared seeded universe, subject to backend authorization and symbol availability.
- Add entry points from Screener rows and Security Detail header/actions in this phase; keep route-compatible handoff patterns for Watchlist rows, Portfolio holding rows, and Seed result rows as those phases are present.
- Present a single focused research packet optimized for reading and comparison rather than tab navigation.
- Show header context: company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations.
- Show source coverage by category: profile, fundamentals, ratios, quote, dividends, valuation, score, and analyst estimates. Coverage labels should support `FMP`, `Yahoo Finance`, `Mixed`, unavailable, and stale states when provided by the backend.
- Show valuation evidence: DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range when available, and MiFID II disclaimer.
- Show cash-generation evidence: FCF TTM/latest annual, FCF history, positive-FCF years, FCF growth, FCF margin when available, and DCF eligibility/data gaps.
- Show earnings evidence: revenue, net income, EPS, earnings history/trend, earnings growth, and quality notes where data is available.
- Show balance-sheet and debt evidence: total debt, cash, net debt, debt-to-equity, current ratio, quick ratio when available, interest coverage when available, and trend context.
- Show historical Recharts graphs for earnings, debt, ROI/ROIC, and ROE, each with source badge, latest data date, unavailable-series handling, responsive desktop/mobile layout, readable axes, tooltips, and accessible summaries.
- Show dividend evidence: dividend yield, dividend history, streak, payout ratio, FCF payout/coverage, dividend CAGR, and dividend sustainability status.
- Show quality and growth evidence: ROIC, ROE, gross/operating/net margins when available, revenue/FCF/EPS CAGR at 3y/5y/10y, and peer/sector context.
- Show risk and data-quality notes: unavailable metrics, stale inputs, provider fallbacks, provider-plan restrictions, source limitations, and model caveats in plain language.
- Provide actions to add to watchlist, add to portfolio when the relevant UI/API contract exists, open custom DCF controls, refresh/seed symbol if allowed, and return to Screener/Security Detail.
- Reuse the frontend stack from `specs/tech-stack.md`: React 18, TypeScript strict mode, Tailwind CSS, Recharts, TanStack Query, React Router, and existing authenticated API-client patterns.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H4A: In-Depth Stock Review Page, following H4 Security Detail UI and before H5 portfolio-builder work. |
| Primary user value | Give users a complete single-stock research packet without forcing them to move across many tabs. |
| Relationship to H4 | H4A reuses H4 data contracts, API clients, formatters, and chart/error patterns, but renders a separate page and component. |
| Route | `/securities/:symbol/review` is the canonical route. It is protected and symbol-driven. |
| Entry points | Screener and Security Detail are required H4A entry points. Watchlist, Portfolio, and Seed result entry points are acceptance targets when those surfaces are available. |
| Data source posture | Use authenticated application APIs backed by local DB/Redis and backend provider fallback. The frontend must not call FMP/Yahoo directly. |
| Source transparency | Provider coverage, freshness, fallback, unavailable, and stale labels are first-class user-facing evidence. |
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

## Feature-spec questions for the user

1. Should H4A aggregate data by composing existing H4 endpoints on the frontend, or should we allow a future backend `review` endpoint if composition becomes too slow or brittle?
2. Should the review page use a fixed section order matching the roadmap, or include a compact table-of-contents with jump links and sticky progress?
3. Should custom DCF open inline on the review page, link back to the H4 Valuation tab, or use a shared drawer/modal component?
4. Should `Add to portfolio` be active in H4A only after H5/F2-compatible UI contracts are present, or should it show a disabled/coming-later state for now?
5. Which entry points are required for the first H4A merge: Screener and Security Detail only, or also Watchlist/Portfolio/Seed if those routes exist locally?

## Out of scope

- New backend ingestion, provider integrations, valuation formulas, alert rules, portfolio construction logic, or screener-filter changes.
- Replacing H4 Security Detail UI or removing its tabbed workflow.
- Personalised advice, trade execution, brokerage integration, or buy/sell instructions.
- Live provider calls from the frontend or UI behaviour that bypasses authenticated backend APIs.
