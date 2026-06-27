# H8 - Seed & Shared Universe UI Plan

1. **Scope alignment and API contracts**
   - Review existing H1-H7 frontend conventions, route layout, auth state, TanStack Query keys, API-client helpers, badges, tables, empty states, and disclaimer components.
   - Review current seed/admin endpoint contracts and determine whether non-admin CSV seeding, named packs, source coverage, fallback reasons, and freshness metadata are already exposed or require narrowly scoped backend support.
   - Confirm the feature-spec questions around named-pack visibility, route label, and admin maintenance scope.
   - Define typed request/response models for seed preview, seed execution, per-symbol results, provider coverage, fallback reasons, freshness, and result links.

2. **Seed entry and preview flow**
   - Add the protected Seed / Shared Universe route and navigation entry using existing route/auth patterns.
   - Build CSV input parsing with trimming, uppercase normalization, duplicate removal, invalid-token feedback, and a pre-submit preview.
   - Show clear copy that seeding creates or refreshes shared platform-wide reference data and does not add symbols to personal watchlists or portfolios.
   - Add named-pack selection for admin users and for non-admin users only when backend policy allows it; otherwise show the agreed hidden or disabled state.

3. **Submission and result handling**
   - Implement TanStack Query mutations for CSV seeding and named-pack seeding through authenticated application APIs.
   - Render partial-success results without blocking successful rows when some symbols fail.
   - Show per-symbol statuses: seeded, refreshed, skipped, failed, unavailable, and any backend-provided error detail.
   - Show provider badges, category-level source coverage, fallback reasons, source freshness, stale/unavailable labels, and provider limitation messages where available.
   - Include result context columns for ticker, company name, sector, exchange, country, description/profile excerpt, current price, fair value, margin of safety, recommendation, and score where returned.

4. **Research handoffs and decision-support guardrails**
   - Link successful rows to Security Detail and In-Depth Review pages.
   - Provide handoff actions to Screener/search so users can continue market-wide research after seeding.
   - Reuse existing watchlist and portfolio add patterns where available, keeping ownership enforcement on the backend.
   - Render MiFID II disclaimers anywhere fair value, margin of safety, recommendation, or score outputs are visible.
   - Preserve loading, empty, preview-only, partial-success, full-success, full-failure, unauthorized, expired-session, stale-data, and provider-limited states.

5. **Quality, accessibility, and merge readiness**
   - Add focused frontend tests for CSV parsing, preview, role-sensitive controls, mutation success/failure, partial results, provider badges, fallback reasons, source freshness, result links, and disclaimer rendering.
   - Add deterministic browser or integration coverage for investor, advisor, and admin seeding journeys without live provider calls or secrets.
   - Run linting, TypeScript checks, tests, production build, and responsive/manual accessibility review.
   - Resolve or explicitly defer the feature-spec questions in `requirements.md` before implementation merge.
