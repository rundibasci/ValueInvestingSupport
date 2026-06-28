# HD2 - Demo Polish Pass

## Purpose

Apply the scoped polish fixes identified during HD1 so the local full-demo experience is stable, coherent, and stakeholder-ready before beta persona testing and formal quality/observability work.

This phase closes the open INGR review-page findings from `specs/2026-06-28-full-demo-assessment/ingr-review-bug-notes.md`, improves local demo readiness, and verifies that the application can be demonstrated with deterministic local data and no live provider secrets.

## Scope

- Apply the full roadmap scope for HD2: review-page fixes, local demo readiness, deterministic full-demo validation, and documented remaining UX gaps.
- Keep the Docker backend image buildable from a Windows checkout by normalizing/executing the Maven wrapper in the Docker build stage or by enforcing repository line endings.
- Make reseeding idempotent for INGR-style review data:
  - No duplicate current-year fundamentals.
  - No duplicate current-date ratios.
  - No retained stale current ratio rows after refresh.
  - No repeated current-year/current-date chart labels after repeated reseeding.
- Normalize percentage rendering on the review page:
  - Decimal ratios such as dividend yield, payout ratio, ROE, ROIC, margins, and debt ratios render as human percentages.
  - Already-percent values such as margin of safety remain correctly scaled.
  - Formatting is field-aware and avoids silently mixing decimals with percentage points.
- Refresh or update watchlist state after `Add to watchlist` succeeds so the button immediately becomes the guarded `Already on watchlist` state and cannot produce an avoidable duplicate `409`.
- Clear contradictory portfolio-add state after a successful add so users see either success or existing-holding state, not both at once.
- Remove Recharts container sizing warnings on the review page and verify charts remain visible across desktop and mobile layouts.
- Improve local demo readiness:
  - Document startup steps.
  - Document seeded credentials and demo URLs.
  - Document deterministic data assumptions.
  - Document known limitations and a short stakeholder walkthrough checklist.
- Verify that the full demo can be run without live FMP/Yahoo calls or committed secrets using deterministic localstack data.
- Run frontend typecheck/build, backend compile/tests where supported, and `git diff --check`.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers HD2: Demo Polish Pass. |
| Scope depth | Use the full HD2 roadmap scope, not a narrower INGR-only or copy-only pass. |
| Validation level | Require full local-demo validation, including build/check commands and a browser walkthrough where supported. |
| Primary input | Use HD1 outputs and `specs/2026-06-28-full-demo-assessment/ingr-review-bug-notes.md` as the concrete bug list. |
| Data posture | Use deterministic localstack/full-demo data and avoid live FMP/Yahoo dependencies or secrets. |
| Fix policy | Fix scoped demo blockers and polish issues directly; defer larger product or architecture changes with clear severity and owner/phase recommendation. |
| User-facing posture | Preserve decision-support framing and MiFID II disclaimers on valuation, score, recommendation, and margin-of-safety surfaces. |

## Context and guardrails

- `specs/mission.md` defines the product journey as market universe seeding, screening/research, fundamental analysis, intrinsic value estimation, margin of safety, recommendation, portfolio construction, and continuous monitoring. HD2 should make that journey demonstrable, not introduce unrelated workflows.
- Mission principle 4 requires the platform to remain decision-support software, not personalized investment advice.
- Mission principle 5 requires cache-first behavior and provider fallback through backend abstractions; the UI must not depend on direct provider calls.
- Mission principle 9 requires seeded securities to remain shared reference data while watchlists and portfolios remain user-owned.
- Mission principle 11 requires the in-depth review page to expose key financial and valuation context with data-availability labels.
- `specs/tech-stack.md` defines React 18, TypeScript, Tailwind CSS, TanStack Query, React Router, Spring Boot, Redis, PostgreSQL/H2, Docker Compose, and Flyway as the relevant implementation environment.
- Treat stale provider rows, missing metrics, unavailable quick ratio, missing dividend history, ineligible DCF, and partial source coverage as visible product states, not hidden failures.
- Do not expose provider secrets, JWT refresh tokens, local credentials beyond intended demo credentials, raw provider payloads that violate display constraints, stack traces, or sensitive user data in UI, fixtures, logs, screenshots, docs, or reports.

## Resolved feature-spec decisions

1. The next phase is HD2: Demo Polish Pass.
2. The spec covers the full HD2 roadmap scope.
3. The validation bar is full local-demo validation.

## Out of scope

- New valuation formulas, score formulas, provider integrations, ingestion schedules, authorization rules, or account lifecycle flows.
- Broad redesigns, new navigation models, new dashboard concepts, new beta persona reports, or feature selection work from HD3/HD4.
- Production observability, Google sign-in, GCP deployment, Terraform, commercial compliance hardening, or live provider-data validation.
- Brokerage/order execution, buy/sell instructions, guaranteed-return language, or personalized investment advice.
