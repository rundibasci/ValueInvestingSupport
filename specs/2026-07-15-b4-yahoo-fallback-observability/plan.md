# Plan - Phase B4: Yahoo Fallback Observability

1. Persistence and recording.
   - Add Flyway V22 for a dedicated fallback-event table and query indexes.
   - Add entity, repository, event command, and failure-safe recorder.
   - Correlate job/run context through existing MDC keys.

2. Provider instrumentation.
   - Instrument explicit profile, fundamentals, ratios, and quote fallbacks.
   - Instrument exchange and volume enrichment.
   - Record successful accepted fields, rejected values, failed calls, trigger, and duration.
   - Preserve existing source tracker, health tracker, cache, and exception behavior.

3. ADMIN analysis API.
   - Add paginated filtered event endpoint.
   - Add aggregate summary endpoint with attempts, successful fallbacks/enrichments, failures, affected symbols, and grouped counts.
   - Add controller/service tests for authorization-compatible response behavior and filters.

4. ADMIN frontend.
   - Add typed API client and `/admin/fallbacks` page.
   - Add summary cards, filters, paginated event table, and sanitized diagnostic details.
   - Add ADMIN navigation and route wiring.

5. Verification.
   - Run targeted Java 21 tests and backend package.
   - Run frontend typecheck and build.
   - Run migration/context tests and `git diff --check`.

