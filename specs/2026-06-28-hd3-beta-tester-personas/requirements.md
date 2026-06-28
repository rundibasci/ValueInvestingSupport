# HD3 - Beta Tester Persona Simulation

## Purpose

Run a structured beta-test pass with three scripted investor personas after the HD2 demo polish pass.

The goal is to evaluate whether the platform supports distinct real-world research styles across the full value-investing cycle: market universe seeding, screening and research, fundamental analysis, intrinsic value estimation, margin of safety review, portfolio construction, and continuous monitoring.

## Scope

- Simulate three beta testers using the full local demo:
  - Very prudent value investor.
  - Hedge-fund asset allocator.
  - Financial journalist / trend observer.
- Each persona must use the platform as a real evaluator would:
  - Discover candidate stocks.
  - Seed or refresh symbols as needed.
  - Open screener/search, security detail, and in-depth review pages.
  - Build or update a model portfolio.
  - Create or update a watchlist.
  - Document impressions, trust signals, confusing states, and workflow gaps.
- Persona behavior may modify seeding. Each persona can seed custom ticker lists or use available seed packs when that behavior fits their investment style.
- Use deterministic localstack/full-demo data as the validation baseline.
- Use human-curated source summaries or fixtures for persona research inputs:
  - Seeking Alpha-style summaries for the prudent value investor.
  - Morningstar-style analyst-note summaries for the allocator.
  - Google News-style headline summaries for the journalist.
- Do not depend on scraping paywalled content or live news access for reproducible validation.
- Produce a report under this spec directory for each persona.
- Produce a combined findings index that groups recommendations by blocker, product gap, UX polish, data-quality concern, and nice-to-have.
- Any discovered bugs or UX issues must include severity, affected surface, reproduction notes, expected behavior, and recommended owner/phase.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers HD3: Beta Tester Persona Simulation. |
| Persona seeding | Personas may modify seeding to simulate realistic investor behavior. Seed actions must remain documented and reproducible. |
| Runtime validation | Require a full local demo run with browser or API evidence for each persona workflow. |
| Source inputs | Use curated summaries or fixtures, not paywalled scraping or non-deterministic live news dependencies. |
| Report location | Store persona reports under `specs/2026-06-28-hd3-beta-tester-personas/`. |
| Product boundary | Reports may evaluate decision support, research confidence, and portfolio/watchlist workflows; they must not produce personalized investment advice or buy/sell instructions. |

## Context and guardrails

- `specs/mission.md` requires every feature to map to the value-investing cycle and preserve the decision-support boundary.
- Mission principle 1 requires data before opinion: persona reports should explain which platform data shaped each decision.
- Mission principle 4 requires MiFID II decision-support framing on fair value, margin of safety, recommendation, and score surfaces.
- Mission principle 8 requires financial resilience to be assessed through trends and context, not universal leverage shortcuts.
- Mission principle 9 allows authenticated users to seed shared reference data while watchlists and portfolios remain user-owned.
- Mission principle 10 requires market-wide research to include business context, not only ticker symbols.
- Mission principle 11 expects single-stock review pages to function as research packets with valuation, cash generation, debt, dividends, source coverage, freshness, and data gaps.
- `specs/tech-stack.md` defines React, TanStack Query, Recharts, Spring Boot, PostgreSQL, Redis, Docker Compose, and localstack/full-demo flows as the relevant surfaces for validation.
- Do not commit provider secrets, JWT refresh tokens, private credentials, raw restricted provider payloads, stack traces, or sensitive user data.
- Avoid implying that persona outputs are actual portfolio recommendations.

## Persona report requirements

Each persona report must include:

- Persona assumptions and investment style.
- Source summaries used and candidate-stock selection rationale.
- Seed actions performed, including ticker lists or packs and any failed/unavailable rows.
- Final portfolio with holdings, weights or quantities, valuation context, and key risks.
- Watchlist with monitoring rationale, target signals, and why each symbol was not added to the portfolio.
- Platform impressions covering usability, trust, data gaps, review pages, portfolio workflow, and watchlist workflow.
- Prioritized improvement recommendations grouped as blockers, product gaps, UX polish, data-quality concerns, and nice-to-have enhancements.
- Validation evidence: routes visited, symbols reviewed, API/browser evidence captured, and any limitations.

## Resolved feature-spec decisions

1. The next phase is HD3: Beta Tester Persona Simulation.
2. Personas may modify seeding as part of realistic investor behavior.
3. Validation requires a full local demo run.

## Out of scope

- Implementing beta-driven product improvements. That belongs to HD4.
- Adding new production data providers, scraping paywalled sources, or depending on live news feeds for deterministic validation.
- Changing valuation formulas, score formulas, authorization rules, or persistence models unless a blocking defect prevents persona simulation.
- GCP deployment, observability implementation, Google sign-in, commercial readiness, or formal compliance hardening.
- Brokerage/order execution, personalized investment advice, or buy/sell instructions.
