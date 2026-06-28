# Requirements - Phase I1: Test Coverage

## Scope

Phase I1 hardens the platform before observability work by adding reliable, repeatable test coverage across the core value-investing workflows already delivered. The scope is full roadmap I1 coverage:

- Unit tests for calculator classes and deterministic domain logic.
- Integration tests for core authenticated APIs, including screener, valuation, auth, portfolio/watchlist-adjacent flows where existing contracts support them.
- Coverage for HD4-selected beta-driven workflows and data-quality states.
- Practical persona replay tests or scripts for the three HD3 user journeys where they can run without paid/live provider calls.

The phase must preserve the decision-support boundary: tests can assert displayed recommendations, score states, valuation availability, and warnings, but should not frame outputs as personalized advice or trading instructions.

## Context

- `specs/mission.md` requires transparency, conservative defaults, explainable missing data, portfolio exposure visibility, and mandatory MiFID II decision-support language on fair-value and score surfaces.
- `specs/tech-stack.md` defines Spring Boot 3, Java 21, Maven, React 18, TypeScript 5, Vite, TailwindCSS, TanStack Query, PostgreSQL, Redis, and H2/Testcontainers-style local validation.
- `specs/roadmap.md` defines Phase I1 as test coverage for calculator classes, screener API, valuation endpoint, auth flow, HD4-selected beta-driven workflows, data-quality states, and deterministic persona replay where practical.
- `specs/2026-06-28-beta-feature-selection/` is the source for HD4 feature-selection decisions and affected workflow expectations.
- `specs/2026-06-28-hd3-beta-tester-personas/` is the source for persona journeys and prudent-value validation findings.

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| I1 scope | Full roadmap I1 coverage | The phase should cover calculators, API workflows, HD4-selected feature behavior, and practical persona replay rather than only the highest-risk gap. |
| Validation strictness | Reliable local/CI validation | Tests must run repeatably without paid provider calls or fragile live dependencies. Local seeded data, mocks, H2, Testcontainers, and deterministic fixtures are preferred. |
| Branch/spec name | `phase-i1-test-coverage` | Roadmap-aligned and concise. |
| Live market data | Excluded from required validation | FMP/Yahoo behavior should be mocked or fixture-backed unless an existing optional profile explicitly supports live integration. |
| Demo replay | Practical, deterministic only | Persona replay is required where it can be automated or scripted with seeded/local data. Manual full-demo evidence can remain optional. |

## Functional Requirements

1. Calculator and domain logic coverage must include DCF, Graham, DDM, Value Score, margin of safety, and any data-availability/status mapping logic introduced by HD4.
2. Auth integration coverage must verify login, refresh or protected-route access where implemented, logout/revocation behavior where existing infrastructure supports it, and role restrictions for admin-only APIs.
3. Screener and valuation integration coverage must verify successful results, empty or unavailable states, stale/missing data behavior, guardrail-blocked valuation cases, and decision-support disclaimer presence where exposed.
4. HD4-selected beta-driven features must receive tests for score/data-quality states, concentration warning thresholds, watchlist rationale persistence, and any implemented conservative-workflow diagnostics.
5. Persona replay coverage must include practical deterministic scripts or tests for the HD3 personas, especially the Agent 1 prudent-value workflow using the 10-symbol validation set where local seed fixtures exist or can be created safely.
6. Tests must isolate platform-owned behavior from provider availability by using mocks, fixtures, seeded DB data, H2, Testcontainers, or existing localstack/demo profiles.
7. Documentation or spec evidence must identify any I1 roadmap item that remains impractical to automate in this phase and name the reason and follow-up owner.

## Non-Goals

- Implementing Phase I2 observability metrics, dashboards, or structured logging.
- Adding new product features beyond small testability hooks or deterministic fixtures required for coverage.
- Reworking valuation, scoring, or portfolio algorithms except to fix defects discovered by tests.
- Requiring a real FMP API key, Yahoo Finance availability, SMTP delivery, Google OAuth, or deployed cloud infrastructure.
- Replacing the full test strategy with brittle screenshot-only or manual-only validation.

## Dependencies And Constraints

- Existing unrelated untracked log files should remain untouched.
- Secrets must never be committed. No test fixture may contain real API keys, JWT private keys, OAuth credentials, or personally sensitive claims.
- Tests should fit the existing Maven/Vite/JUnit/React Testing Library patterns already present in the repository.
- Any new seeded data should be small, deterministic, and clearly marked as test/demo fixture data.
