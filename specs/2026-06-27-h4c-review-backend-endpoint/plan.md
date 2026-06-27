# H4C - Review Page Backend Review Endpoint Plan

1. **Contract and implementation review**
   - Review the existing H4A/H4B `SecurityReviewPage` data composition, query keys, loading/error states, chart inputs, source/freshness labels, custom DCF controls, watchlist action, and portfolio-add action.
   - Review the backend endpoints and services that currently power the review page: profile, financials, ratios, financial health, valuation, dividends, growth, peers, score, watchlist-adjacent state where relevant, and security lookup.
   - Identify reusable DTOs, mappers, repositories, service methods, error types, authorization checks, and stale-data guards.
   - Confirm what source coverage and freshness metadata already exists in persisted records or service responses, and list any metadata that must be explicitly marked unavailable.

2. **Backend review contract**
   - Define the `SecurityReviewResponse` DTO as a nested research packet aligned to the review page sections.
   - Include profile/header data, source coverage, freshness metadata, valuation evidence, cash-generation evidence, earnings evidence, balance-sheet/debt evidence, historical chart series, dividend evidence, quality/growth evidence, peers, score, data-quality notes, and disclaimer text.
   - Reuse existing DTOs or section DTOs where practical so the new contract does not fork business logic from existing endpoints.
   - Model unavailable, stale, unsupported, provider-limited, and fallback states explicitly instead of omitting fields or substituting zero.
   - Document the response contract in tests and controller-level expectations.

3. **Backend aggregation service and endpoint**
   - Add a review aggregation service that reads from existing local DB-backed services and valuation/score services.
   - Add `GET /api/v1/securities/{symbol}/review` under the existing authenticated security-detail controller area or a closely matching controller pattern.
   - Preserve existing role access: authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users can read review packets for shared seeded-universe symbols.
   - Preserve existing not-found, stale, unavailable, validation, and authorization error semantics.
   - Ensure aggregation failures are section-aware where useful: partial data can render with explicit data-quality notes when the underlying contract supports partial availability, while true missing symbols still fail clearly.
   - Avoid frontend-visible stack traces, raw provider payloads, secrets, or internal diagnostics.

4. **Frontend migration**
   - Add a typed frontend API client method and TanStack Query hook for `GET /api/v1/securities/{symbol}/review`.
   - Update `SecurityReviewPage` to use the new review response as its primary page data source.
   - Preserve existing H4A/H4B visual sections, charts, table of contents, scroll progress, source/freshness labels, data-unavailable labels, inline custom DCF controls, watchlist action, portfolio-add action, and navigation entry points.
   - Remove obsolete multi-endpoint page composition only where the new endpoint fully replaces it; keep separate mutations such as custom DCF, watchlist, and portfolio-add on their established APIs.
   - Keep signed-out, loading, empty, stale, partial-data, unavailable, and error states consistent with the existing app.

5. **Tests and verification**
   - Add backend unit tests for the aggregation service, DTO mapping, source/freshness metadata, unavailable metrics, stale data, and provider fallback labels.
   - Add backend controller tests for authenticated access, unauthenticated rejection, seeded symbol success, unknown symbol, stale/unavailable data, and partial-data responses where supported.
   - Add frontend tests for the new query hook and `SecurityReviewPage` rendering from one review response.
   - Add regression coverage that all required review sections still render: DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, source coverage, freshness, historical charts, and unavailable-data labels.
   - Verify H4B portfolio-add and existing watchlist/custom DCF actions still work after the page data-source migration.
   - Run backend tests, frontend lint/typecheck/tests/build, and `git diff --check` before merge.
