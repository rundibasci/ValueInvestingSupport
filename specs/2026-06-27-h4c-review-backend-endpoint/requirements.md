# H4C - Review Page Backend Review Endpoint

## Purpose

Implement a dedicated backend review endpoint that assembles the complete single-stock research packet for the In-Depth Stock Review page in one authenticated API call.

This phase supports the `Fundamental Analysis -> Intrinsic Value Estimation -> Margin of Safety Calculation -> Recommendation` steps in `specs/mission.md` by making the review page faster, more reliable, and more explicit about source coverage and freshness. It preserves the platform boundary as a decision-support tool, not regulated investment advice.

## Scope

- Add an authenticated endpoint, `GET /api/v1/securities/{symbol}/review`, available to every authenticated role for symbols in the shared seeded universe.
- Assemble the full review packet server-side: profile, financials, ratios, financial health, valuation, dividends, growth, peers, score, source coverage, freshness metadata, data-availability labels, and decision-support disclaimer content where relevant.
- Reuse existing backend services, repositories, DTO mapping patterns, authorization rules, cache/provider abstractions, and error handling from the H4/E/D/F era endpoints wherever possible.
- Prefer a nested response DTO that mirrors the review page sections while wrapping existing endpoint DTOs where that lowers risk and avoids duplication.
- Include source coverage by category: profile, fundamentals, ratios, quote, dividends, valuation, score, peers, and analyst estimates when available.
- Include freshness and staleness metadata by category, including provider, latest data date/timestamp, fallback status, unavailable state, and known provider limitation where the existing data model can support it.
- Preserve RULE-06 and other valuation eligibility behavior already enforced by the valuation services. The review endpoint must not invent unsupported valuation outputs.
- Update `SecurityReviewPage` at `/securities/:symbol/review` to consume the new backend review endpoint instead of composing many individual H4 endpoints on the frontend.
- Preserve all H4A/H4B review-page sections, charts, data-quality labels, source/freshness indicators, custom DCF controls, watchlist action, portfolio-add action, and entry points.
- Keep frontend compatibility with React 18, TypeScript strict mode, Tailwind CSS, Recharts, TanStack Query, React Router, and the existing authenticated API-client conventions.
- Add focused backend and frontend tests for the new endpoint, DTO mapping, source/freshness metadata, error states, and review-page migration.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H4C: Review Page - Backend Review Endpoint. |
| Primary user value | Reduce review-page latency and fragility by replacing frontend multi-endpoint composition with one server-side research-packet endpoint. |
| Scope shape | H4C includes both the backend endpoint and the frontend migration to consume it, matching the roadmap. |
| Endpoint | `GET /api/v1/securities/{symbol}/review` is the canonical authenticated endpoint. |
| Response shape | Use a nested review DTO aligned to page sections, reusing or wrapping existing DTOs where practical. |
| Aggregation source | Aggregate from existing local DB-backed services, valuation/score services, and cache-aware market-data abstractions. The frontend must not call FMP/Yahoo directly. |
| Caching | Do not add a new aggregate review-response cache by default. Rely on existing lower-level DB/Redis/cache behavior unless implementation profiling exposes a clear need. |
| Authorization | Any authenticated role can access the review packet for seeded shared-universe symbols. Backend authorization remains authoritative. |
| Missing data | Missing values are reported as unavailable, stale, unsupported, or provider-limited. The endpoint and UI must not substitute zero or infer unsupported metrics. |
| Advice boundary | Fair value, margin of safety, recommendation, score, and valuation language remains descriptive and non-directive. MiFID II disclaimer is mandatory in valuation/recommendation contexts. |

## Context and guardrails

- H4A intentionally composed existing H4 endpoints on the frontend and deferred a dedicated backend review endpoint to H4C.
- H4B added portfolio-add behavior to the same review page. H4C must preserve that behavior and must not weaken portfolio ownership rules.
- Mission principle 1 requires data before opinion.
- Mission principle 2 requires valuation transparency, including inputs and assumptions.
- Mission principle 4 requires the system to remain decision support, not regulated investment advice.
- Mission principle 5 requires cache-first external data behavior and Yahoo Finance fallback through backend abstractions, never from the frontend.
- Mission principle 8 requires financial resilience to be shown through leverage, liquidity, interest burden, cash generation, and dividend coverage over time, not reduced to universal pass/fail ratios.
- Mission principle 9 requires shared research-universe data while keeping watchlists and portfolios user-owned.
- Mission principle 11 requires the in-depth review page to expose DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, source coverage, freshness, and unavailable-data labels.
- `specs/tech-stack.md` defines Spring Boot 3.x, Java 21, Spring Security JWT, Spring Data JPA, Flyway, Redis-backed cache behavior, React 18, TypeScript strict mode, Tailwind CSS, Recharts, TanStack Query, and React Router as the relevant implementation constraints.
- Historical financial snapshots remain immutable. The review endpoint reads the latest relevant records but does not overwrite financial history.
- The endpoint must not expose provider secrets, JWT refresh tokens, raw credentials, stack traces, internal diagnostics, raw provider payloads that violate data-display constraints, or sensitive user data.
- Tests and demos must be deterministic and must not require live FMP/Yahoo calls.

## Resolved feature-spec decisions

1. **Backend and frontend together:** H4C implements the backend aggregation endpoint and updates `SecurityReviewPage` to consume it in the same phase.
2. **DTO strategy:** H4C uses a nested review DTO aligned to review-page sections, reusing or wrapping existing endpoint DTOs where practical to avoid duplicate mapping logic.
3. **Caching strategy:** H4C does not introduce a new aggregate review-response cache by default. Existing service-level DB/cache/provider behavior remains the first line of performance and resilience.

## Out of scope

- New valuation formulas, score formulas, screener filters, ingestion jobs, provider integrations, portfolio algorithms, alert rules, or brokerage/order execution.
- Replacing the review page's information architecture, chart set, custom DCF workflow, watchlist action, or portfolio-add action beyond adapting them to the new data source.
- Live provider calls from the frontend.
- Broad redesign of H4 Security Detail UI, H5 Portfolio UI, H6 Watchlist/Alerts UI, H7 Dashboard, or H8 Seed UI.
- Personalised investment advice, buy/sell instructions, guaranteed-return language, or trade execution.
