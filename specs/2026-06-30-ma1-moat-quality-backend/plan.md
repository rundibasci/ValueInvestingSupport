# MA1 Moat & Quality Backend Plan

1. Persistence model
   - Add enums and entities for moat assessment, capital allocation, valuation bands, and stability results.
   - Add Spring Data repositories with latest-result lookup methods.
   - Add Flyway migrations for PostgreSQL and H2.

2. Service layer
   - Implement `MoatAssessmentService.analyze(symbol)`.
   - Implement `CapitalAllocationService.analyze(symbol)`.
   - Implement `ValuationHistoryService.compute(symbol)`.
   - Implement `StabilityService.assess(symbol)`.
   - Keep calculations deterministic, null-aware, and local-DB-only.

3. API and DTOs
   - Add controller endpoints for moat, capital allocation, and valuation bands.
   - Add response DTOs including availability fields and underlying values.
   - Return 404 for unknown symbols and partial result metadata for insufficient history.

4. Review endpoint integration
   - Extend the security review response with moat, capital allocation, valuation bands, and stability sections.
   - Reuse the new services to compute and persist fresh results when a review is requested.

5. Screener integration
   - Extend `ScreenerRequest` with moat strength and shares outstanding trend filters.
   - Join latest persisted moat and capital allocation rows in `ScreenerService`.

6. Validation
   - Add unit tests for ROIC consistency/moat classification, shares trend classification, valuation percentile computation, and stability criteria.
   - Run backend tests.
   - Run frontend typecheck/build only if frontend contracts are touched.
   - Run `git diff --check`.

7. Documentation and handoff
   - Update the Obsidian activity note with implemented behavior and validation evidence.
   - Use merge workflow to update changelog, commit, push, and merge to `main`.
