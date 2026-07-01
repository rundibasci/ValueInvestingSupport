# SC2 Universe Curation UI & Workflow Requirements

## Scope

- Add a React Universe Curation workflow backed by the SC1 admin universe selection APIs.
- Allow administrators to choose built-in templates, adjust filters, preview matching symbols, and seed the previewed universe.
- Show result tables with symbol, company name, exchange, sector, market cap, and per-symbol seed status.
- Display count and cap warnings before seeding so large universes are not started accidentally.
- Keep the seeded universe connected to search and screener by using existing seed APIs and invalidating frontend query state after seed completion.

## Exclusions

- Group K, K1, K2, and K3 cloud work is explicitly excluded.
- Backend persistence for exclusion controls is not introduced in this phase unless already present.
- Saved custom templates are not added; the UI consumes built-in templates from SC1.
- Long-running asynchronous ingestion orchestration is not added; seeding follows the current synchronous SC1/seed behavior.

## Decisions

- The next phase was selected as SC2 because SC1 is complete and merged, and SC2 is the first unstarted non-K roadmap phase.
- The page is implemented in the React frontend rather than static demo HTML because the roadmap names the React frontend and builds on H8/shared-universe patterns.
- The UI supports ADMIN as the primary role. If existing route guards expose the page to other authenticated users, restricted actions remain shaped by backend authorization.
- Restriction controls are represented only when a backend capability can be discovered in the current codebase; otherwise the UI explains unavailable persistence through disabled controls and does not fake behavior.

## Assumptions

- SC1 responses expose templates, criteria preview, capped warnings, and seed results in JSON shapes already covered by backend tests.
- Existing frontend auth stores a bearer token or API client context suitable for admin endpoints.
- The current frontend does not have a dedicated active-universe summary endpoint; summary cards can use the latest preview/seed result as the immediately actionable state.
- Links to ingestion event details can be generated only when seed responses include event identifiers or existing routes support them.

## Dependencies

- `GET /api/v1/admin/universe/templates`.
- `POST /api/v1/admin/universe/preview`.
- `POST /api/v1/admin/universe/seed`.
- Existing React Router, TanStack Query, TailwindCSS, and frontend API/auth conventions.
- Existing screener/search query invalidation conventions where present.
