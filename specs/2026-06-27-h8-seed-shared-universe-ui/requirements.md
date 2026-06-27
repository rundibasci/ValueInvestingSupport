# H8 - Seed & Shared Universe UI

## Purpose

Deliver the authenticated React UI for seeding symbols into the platform-wide research universe, making it clear that seeding creates or refreshes shared reference data rather than personal watchlist or portfolio entries.

This phase supports the `Market Universe Seeding -> Screening / Research -> Fundamental Analysis` steps in `specs/mission.md`. It preserves the platform's decision-support boundary while giving every authenticated role a practical way to start research from a ticker list.

## Scope

- Add or complete a protected Seed / Shared Universe route in the React frontend.
- Allow authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users to seed custom ticker CSV lists for research.
- Allow `ADMIN` users to seed named packs for common markets or strategies. Investor/advisor access to named packs is controlled by backend quota/cost policy and must be reflected by the UI.
- Make the shared-universe scope explicit before submission: successful seeding creates or refreshes platform-wide securities, profiles, fundamentals, ratios, quotes, valuations, and scores. It does not create personal watchlist or portfolio entries.
- Normalize and preview CSV tickers before submission, including trimming, duplicate removal, uppercase display, and inline invalid-token feedback.
- Show seed status per symbol after submission: seeded, refreshed, skipped, failed, or unavailable on the current provider plan.
- Show provider/source coverage per symbol, including `FMP`, `Yahoo Finance`, or `Mixed` badges and category-level source data when available: profile, fundamentals, ratios, quote, dividends, valuation, and score.
- Show fallback reasons when Yahoo Finance is used instead of FMP: quota exceeded, FMP unavailable, FMP key missing, provider plan restriction, symbol not available from FMP, or unknown fallback.
- Show source freshness, stale/unavailable states, and provider limitations in plain language.
- Show result context per symbol: ticker, company name, sector, exchange, country when available, company description/profile excerpt, current price, fair value, margin of safety, recommendation, data source, and error detail.
- Link successful rows to Security Detail, In-Depth Review, Screener/search handoff, and user-owned watchlist/portfolio flows where the existing routes and permissions support those actions.
- Preserve MiFID II decision-support disclaimers wherever fair value, margin of safety, recommendation, or score outputs appear.
- Reuse the established frontend stack from `specs/tech-stack.md`: React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Router, and existing authenticated API-client patterns.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H8: Seed & Shared Universe UI, following H7 in the frontend roadmap. |
| Primary user value | Let any authenticated user start market research by seeding symbols into the shared research universe, then move into screener, detail, review, watchlist, or portfolio workflows. |
| Shared data posture | Seeded securities and financial data are platform-wide reference data discoverable by all authenticated users; watchlists and portfolios remain user-owned. |
| Data source posture | The frontend calls authenticated application APIs only. It must not call FMP or Yahoo Finance directly. |
| Authorization | The UI mirrors backend authorization. Investors/advisors can seed CSV lists; admin-only named-pack and maintenance actions stay hidden or disabled when not permitted. |
| Provider transparency | Every result row should explain whether FMP, Yahoo Finance, or mixed provider coverage produced the displayed data, with category-level source/freshness details when returned by the API. |
| Result handling | Partial success is expected. Failed symbols remain visible with actionable error detail and must not block successful symbols. |
| Advice boundary | Fair value, margin-of-safety, recommendation, and score language remains descriptive and non-directive. MiFID II disclaimer is mandatory in those contexts. |
| Route label | Use `Seed Universe` in primary navigation. |
| Named-pack visibility | Hide named packs from non-admin roles until backend quota/cost policy explicitly allows them. |
| Admin maintenance scope | Defer an existing-universe maintenance table; H8 includes CSV seeding and admin named-pack seeding only. |

## Context and guardrails

- Mission principle 1 requires data before opinion.
- Mission principle 4 requires the platform to remain a decision-support tool, not regulated investment advice.
- Mission principle 5 requires cache-first external-data behavior and Yahoo Finance fallback through backend abstractions, never direct frontend provider calls.
- Mission principle 9 requires a shared research universe for every authenticated user while preserving personal ownership of watchlists and portfolios.
- Mission principle 10 requires market-wide research rows to include enough company context to decide whether a stock deserves deeper analysis.
- Mission principle 11 requires handoff to a complete in-depth stock review page so users can verify valuation, financial health, dividends, source coverage, freshness, and data gaps.
- `specs/tech-stack.md` defines React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Router, Spring Boot APIs, Redis-backed cache behavior, and provider fallback as the relevant implementation constraints.
- Treat missing provider data as a first-class state. Do not substitute zeroes, infer unsupported metrics, or hide provider-plan limitations.
- Do not expose provider secrets, JWT refresh tokens, raw credentials, raw provider payloads that violate display constraints, stack traces, or sensitive user data in UI, fixtures, logs, or debug panels.
- Tests and demos must be deterministic and must not require live FMP/Yahoo calls.

## Resolved feature-spec decisions

1. Named seed packs are hidden from investors/advisors until backend quota/cost policy explicitly allows them.
2. The first navigation label is `Seed Universe`.
3. The first implementation includes CSV seeding and admin named-pack seeding. Existing-universe maintenance is deferred.

## Out of scope

- New provider integrations, new ingestion jobs, new valuation formulas, new score formulas, or changes to backend fallback policy.
- Personal watchlist or portfolio creation as a side effect of seeding.
- Brokerage/order execution, buy/sell instructions, guaranteed-return language, or personalized investment advice.
- Google sign-in, observability, GCP deployment, and commercial compliance hardening.
- Live FMP/Yahoo calls from the frontend.
