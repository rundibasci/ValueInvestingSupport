# JC1 Implementation Plan

1. Inspect Existing Job Infrastructure
   - Locate job services, job run log persistence, admin job controllers, and tests.
   - Identify canonical job names and existing summary fields.
   - Confirm migration naming and repository package patterns.

2. Add Ingestion Event Persistence
   - Create `IngestionEvent` JPA entity with job run ID, job name, symbol, data type, status, error detail, source, and timestamp.
   - Add Flyway migration and indexes for run ID, symbol, status, and job name.
   - Add repository query methods for filtered event lookup.

3. Add Admin Job Read API
   - Add DTOs for registered job summaries, run-history pages, and event pages.
   - Implement service methods that map job registry and `JobRunLog` data to API responses.
   - Add `GET /api/v1/admin/jobs`.
   - Add `GET /api/v1/admin/jobs/{jobName}/history?page=&size=`.
   - Add `GET /api/v1/admin/jobs/{jobName}/events?runId=&symbol=&status=`.

4. Emit Events From Existing Ingestion Paths
   - Add a small event recording service with no-op-safe helpers.
   - Instrument ticker-level ingestion work where symbols and data types are known.
   - Keep behavior unchanged if event persistence fails only when the surrounding transaction already fails.

5. Tests
   - Add repository and service/controller tests for event filtering and job DTO mapping.
   - Verify admin endpoints return expected shapes and reject non-admin access if existing patterns support it.
   - Update existing tests impacted by DTO or controller changes.

6. Documentation And Validation
   - Update this spec if implementation requires a narrower event-emission boundary.
   - Run backend tests for job/admin/ingestion areas, then the full backend test suite if feasible.
   - Run frontend build only if frontend files change.
