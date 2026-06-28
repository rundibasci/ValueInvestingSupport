# HD1 - Full Demo UI Assessment

## Purpose

Assess the completed frontend MVP as a coherent, clickable product demo before the project moves into formal quality and observability work.

This phase validates the full `Market Universe Seeding -> Screening / Research -> Fundamental Analysis -> Intrinsic Value Estimation -> Margin of Safety Calculation -> Recommendation -> Portfolio Construction -> Continuous Monitoring` journey described in `specs/mission.md`. It specifically verifies that the newest H8 Seed & Shared Universe UI fits the product and that any rough edges are either fixed safely or captured clearly for follow-up.

## Scope

- Run the localstack/full-demo environment and React frontend against deterministic local data.
- Walk through the complete authenticated user journey: login, dashboard, seed universe, screener, security detail, in-depth review, watchlist, portfolio builder, rebalancing, and alerts.
- Verify the H8 Seed & Shared Universe UI in depth:
  - CSV preview, duplicate removal, invalid ticker feedback, and submission states.
  - Admin named-pack seeding visibility and non-admin hiding behavior.
  - Source badges, fallback messaging, freshness labels, partial-success rows, failed-row errors, and handoffs to Screener, Security Detail, and In-Depth Review.
  - Clear explanation that seeding creates shared reference data and does not create personal watchlist or portfolio entries.
- Assess look and feel across all primary React surfaces:
  - Visual hierarchy, spacing, density, table readability, forms, badges, buttons, focus states, loading states, empty states, error states, and mobile/desktop responsiveness.
  - Consistency of navigation labels, route transitions, page headings, action placement, and decision-support disclaimers.
  - No text overlap, cramped controls, misleading color-only states, or marketing-style pages where an operational workflow is expected.
- Create a concise assessment report under this spec directory with findings grouped as blockers, polish fixes, accessibility issues, copy issues, and deferred improvements.
- Fix low-risk visual and copy issues immediately when they are clearly scoped and do not change backend behavior.
- Defer larger UX, product, backend, authorization, data-model, or provider changes into explicit follow-up roadmap items rather than expanding this phase.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers HD1: Full Demo UI Assessment, following H8 in the frontend roadmap. |
| Assessment breadth | Include the full authenticated demo journey and all primary React surfaces built through H8. |
| Runtime requirement | Run everything: localstack/full-demo backend path plus the React frontend against deterministic local data. Static/code review is only a documented fallback if the environment is blocked. |
| Fix policy | Include immediate low-risk UI/copy fixes when they are obvious, scoped, and do not change backend behavior. |
| Report structure | Findings are grouped exactly as roadmap requests: blockers, polish fixes, accessibility issues, copy issues, and deferred improvements. |
| Seed-universe focus | H8 receives special attention because it is the newest shared-universe workflow and affects the start of the research cycle. |
| Advice boundary | Fair value, margin of safety, recommendation, and score displays remain decision-support only and must show MiFID II disclaimers. |
| Data posture | Demo assessment uses deterministic local data and must not require live FMP/Yahoo calls or secrets. |

## Context and guardrails

- Mission principle 1 requires data before opinion: the demo must show financial context before recommendation-like outputs.
- Mission principle 4 requires the platform to remain a decision-support tool, not regulated investment advice.
- Mission principle 5 requires cache-first external-data behavior and Yahoo Finance fallback through backend abstractions, not direct frontend provider calls.
- Mission principle 9 requires seeded securities to be shared reference data while watchlists and portfolios remain user-owned.
- Mission principle 10 requires market-wide research rows to include company context, not only ticker symbols.
- Mission principle 11 requires the in-depth review experience to expose valuation, financial health, dividends, source coverage, freshness, and data gaps.
- `specs/tech-stack.md` defines React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Router, Spring Boot APIs, Redis-backed cache behavior, PostgreSQL/H2 local data, and provider fallback as the relevant constraints.
- Treat missing provider data, stale data, unavailable metrics, and plan restrictions as first-class visible states.
- Do not expose provider secrets, JWT refresh tokens, credentials, raw provider payloads that violate display constraints, stack traces, or sensitive user data in UI, fixtures, logs, screenshots, reports, or debug panels.
- Assessment findings should be practical and merge-oriented: severity, affected surface, observed behavior, expected behavior, and recommendation.

## Resolved feature-spec decisions

1. Include the full HD1 scope: environment run, end-to-end assessment, H8 verification, visual/copy/accessibility review, report output, and low-risk fixes.
2. Require running the localstack/full-demo environment and React frontend against deterministic local data.
3. Use the exact roadmap finding categories in the assessment report.

## Out of scope

- New backend endpoints, provider integrations, ingestion jobs, valuation formulas, score formulas, or authorization rules.
- Large redesigns, new navigation models, new product surfaces, or broad component-system rewrites.
- Live FMP/Yahoo calls, committed secrets, production deployment, GCP infrastructure, observability implementation, Google sign-in, or commercial compliance hardening.
- Brokerage/order execution, buy/sell instructions, guaranteed-return language, or personalized investment advice.
