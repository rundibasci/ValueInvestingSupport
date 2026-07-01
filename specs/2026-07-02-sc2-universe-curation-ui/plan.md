# SC2 Universe Curation UI & Workflow Plan

1. Frontend API contract
   - Add typed client methods for SC1 templates, preview, and seed endpoints.
   - Model universe criteria, preview rows, template responses, and seed results.
   - Keep request payloads aligned with existing backend DTO names.

2. Universe curation workflow page
   - Add a routed Universe Curation page accessible from the authenticated app navigation.
   - Build a filter builder for exchanges, countries, sectors, market cap range, minimum volume, and max symbols.
   - Add a template selector that pre-fills the filter builder from SC1 templates.
   - Add preview and seed actions with loading, error, capped-warning, and per-symbol result states.

3. Active universe and restriction visibility
   - Show an active universe summary using preview/seed state and existing search/screener assumptions where no dedicated summary endpoint exists.
   - Surface exclusion controls as a clearly marked deferred backend dependency when persistence is unavailable.
   - Link seed results to ingestion-event history when an event identifier is available from backend responses.

4. App integration and styling
   - Reuse existing dashboard/page patterns, controls, tables, and status chips.
   - Keep the page dense and operational rather than marketing-oriented.
   - Ensure text and controls fit on mobile and desktop viewports.

5. Validation
   - Run frontend typecheck and build.
   - Run focused backend tests for SC1 API compatibility if frontend typing reveals a contract mismatch.
   - Review git status and diff before vault logging and merge.
