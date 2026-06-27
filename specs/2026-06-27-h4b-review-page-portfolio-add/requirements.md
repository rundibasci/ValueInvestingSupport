# H4B - Review Page Portfolio-Add Integration

## Purpose

Replace the disabled "coming soon" add-to-portfolio action on the In-Depth Stock Review page with a functional, authenticated workflow that lets an investor or advisor add the reviewed symbol to one of their user-owned portfolios.

This phase connects the `Fundamental Analysis -> Portfolio Construction` steps in `specs/mission.md`: after reviewing a seeded stock's valuation, financial resilience, source coverage, and data-quality caveats, the user can act inside the platform by adding that symbol to a personal portfolio model. The action remains decision support, not trade execution or regulated investment advice.

## Scope

- Update `SecurityReviewPage` at `/securities/:symbol/review` so the add-to-portfolio action is functional instead of disabled.
- Reuse the existing portfolio CRUD and holdings APIs delivered by Group F and the client/mutation patterns established by H5.
- Let authenticated `INVESTOR`, `ADVISOR`, and permitted `ADMIN` users select one of their own portfolios and add the reviewed symbol as a holding.
- Require the backend to remain authoritative for portfolio ownership, role access, duplicate handling, validation, and not-found responses.
- Show portfolio choices from the authenticated user's portfolios only.
- Provide clear empty-state guidance when the user has no portfolios yet.
- Support duplicate-holding outcomes explicitly: if the API rejects duplicates, show a recoverable message; if the API updates/merges holdings, show what changed.
- Preserve the H4A review-page research packet, source coverage, DCF controls, watchlist action, and entry points.
- Keep MiFID II decision-support disclaimer visible anywhere valuation-derived context, recommendation, margin of safety, score, or portfolio impact appears.
- Add focused tests for ownership-safe portfolio selection, add-holding mutation behavior, duplicate handling, empty states, error states, and no regression to the H4A review packet.

## Decisions

| Topic | Decision |
|---|---|
| Roadmap phase | This spec covers H4B: Review Page - Portfolio-Add Integration. |
| Primary user value | Let a user move directly from a complete single-stock review packet into a personal portfolio model. |
| Backend scope | Reuse existing portfolio CRUD and holdings endpoints. Do not add backend endpoints unless an existing contract gap makes the review-page action impossible. |
| Frontend route | Keep the action on `/securities/:symbol/review`; do not create a separate route for this phase. |
| Portfolio ownership | Backend responses define what portfolios and mutations are allowed. The frontend must not infer or bypass ownership rules. |
| Data flow | Use existing authenticated frontend API client, TanStack Query, mutation, invalidation, and error-handling conventions. |
| Duplicate holdings | Surface the API outcome clearly and safely. Do not silently create duplicate holdings if the backend contract forbids them. |
| Advice boundary | Adding to portfolio is model-portfolio support only. No trade execution, brokerage integration, or buy/sell instruction language. |

## Context and guardrails

- H4A intentionally shipped the add-to-portfolio action as visible but disabled with a "coming soon" label.
- H4B depends on the portfolio CRUD API from F2 and the frontend portfolio patterns from H5 being available in the active codebase.
- Mission principle 4 requires the platform to remain decision support, not investment advice.
- Mission principle 9 requires shared research-universe data while keeping portfolios user-owned.
- Mission principle 11 requires the in-depth review page to remain a complete single-stock research packet. H4B must not reduce or hide review-page evidence to make room for the portfolio flow.
- `specs/tech-stack.md` calls for React 18, TypeScript strict mode, Tailwind CSS, TanStack Query, React Router, React Hook Form where useful, and authenticated REST/JSON APIs.
- The frontend must never call FMP or Yahoo Finance directly and must not expose provider secrets, JWT refresh tokens, raw credentials, stack traces, or sensitive user data.
- Tests and demos must be deterministic and must not require live FMP/Yahoo calls.

## Resolved feature-spec decision

H4B offers a portfolio picker plus a clear link/action to the existing Portfolio page for portfolio creation. It does not add inline quick portfolio creation on the review page. This keeps the review page focused on research-to-portfolio handoff and avoids duplicating H5 portfolio creation behavior.

## Out of scope

- New portfolio allocation algorithms, simulation logic, rebalancing logic, brokerage/order execution, portfolio accounting, or P&L tracking.
- New market-data ingestion, valuation formulas, screener filters, alert rules, or provider integrations.
- Replacing the H4A research packet layout or moving the review page into the portfolio feature area.
- Broad redesign of H5 portfolio UI, H6 watchlist/alerts UI, or H7 dashboard.
