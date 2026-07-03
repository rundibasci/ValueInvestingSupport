# Requirements - Phase RCL1: Screener And Symbol Recycling Pass

## Scope

RCL1 hardens the first investor-replay recycling findings before Group K work can begin. The phase focuses on the screener API/UI and Berkshire Hathaway class B symbol consistency across seed, review, watchlist, comparison, and portfolio surfaces.

The selected roadmap phase is the first unstarted RCL phase before Group K in `specs/roadmap.md`.

## Required Outcomes

1. Screener API input handling is defensive.
   - Empty or minimal screener requests do not produce `500`.
   - Fractional thresholds such as `0.15` are either normalized to the platform's percentage convention or rejected with `400` field-level validation.
   - UI-standard percentage thresholds such as `15` keep working.

2. Conservative screener and Agent 1 comparison are not contradictory.
   - The screener empty state distinguishes "no screener table results" from "comparison/watchlist candidates are still available".
   - The typo `companyies` is fixed.
   - Agent 1 comparison copy identifies its data source/snapshot when it can differ from current review data.

3. Screener page accessibility structure is corrected.
   - The route exposes one primary `main` landmark.
   - Existing navigation and table interactions remain usable.

4. Berkshire class B symbols are canonicalized.
   - `BRK.B` and `BRK-B` resolve consistently for securities, review, watchlist, comparison, portfolio enrichment, and seeding paths.
   - Existing demo evidence that uses either symbol no longer creates missing-price or missing-review artifacts solely because of punctuation.

5. Investor replay issues have log-correlation evidence.
   - Validation captures route, payload, timestamp, status code, and relevant backend/frontend log excerpts for fixed cases.

## Out Of Scope

- Group K cloud deployment.
- Full historical chart cleanup from RCL3.
- Full beta tester fix pack from RCL4.
- Changing valuation algorithms beyond symbol canonicalization side effects.
- Real portfolio CSV beta workflow from RCL4.

## Decisions

- Treat `BRK-B` as the provider/storage canonical symbol when the current seeded data already uses it, while allowing `BRK.B` as a display/research alias.
- Prefer explicit validation errors over silent guesses when screener numeric payloads are ambiguous.
- Keep all copy decision-support oriented and avoid trade advice.
- Keep fixes narrow enough to validate quickly before the next RCL cycle.

## Assumptions

- The current local demo stack may contain artifacts from previous beta runs; implementation must not depend on those artifacts.
- Existing authentication and seeded demo users remain available for manual replay.
- The frontend stack remains React, TypeScript, TailwindCSS, TanStack Query, and Recharts.
- The backend stack remains Spring Boot 3, Java 21, JPA, and Flyway.

## Context

Mission principles that drive this phase:

- Missing data must be explainable.
- Portfolio exposure must be visible before action.
- Research rationale belongs with the workflow.
- The platform is decision-support, not regulated investment advice.

Tech-stack constraints:

- API DTOs should expose structured availability rather than relying on text parsing.
- Screener/search rows should include enough business context for research.
- Frontend server state is managed through TanStack Query and authenticated REST calls.
