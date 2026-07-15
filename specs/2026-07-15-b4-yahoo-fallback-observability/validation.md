# Validation - Phase B4: Yahoo Fallback Observability

## Automated Checks

- Targeted fallback-client, recorder, and ADMIN service/controller tests on Java 21.
- `./mvnw -DskipTests package` on Java 21.
- `npm run typecheck` and `npm run build` in `frontend`.
- `git diff --check`.

## Acceptance Checks

- Successful FMP responses requiring no enrichment create no fallback event.
- Missing exchange and volume create enrichment events only when Yahoo is attempted.
- Accepted Yahoo exchange/volume values create `SUCCESS` events with exact accepted field names.
- Empty Yahoo enrichment creates `REJECTED`, not `SUCCESS`.
- FMP `PLAN_RESTRICTION` followed by accepted Yahoo data creates a successful explicit-fallback event.
- Failed Yahoo calls create `FAILED` events without leaking secrets or changing the existing provider exception contract.
- Events contain symbol, operation, type, outcome, trigger, providers, duration, time, and job correlation when available.
- ADMIN can filter and inspect events and view aggregate counts at `/admin/fallbacks`.
- Non-admin users cannot access the route or API under existing ADMIN authorization rules.

## Manual QA

- Run the real demo with FMP configured and open `/admin/fallbacks` as ADMIN.
- Seed a controlled ticker and confirm any Yahoo attempt identifies its category and accepted fields.
- Filter by symbol and `PLAN_RESTRICTION` and compare the result with backend health/provider behavior.
- Confirm no API key, authorization header, cookie, crumb, or raw provider response is visible.

## Merge Criteria

- All feature-specific tests pass.
- Backend and frontend compile successfully.
- No secret material or transient runtime artifact is committed.
- Existing market-data outputs remain backward compatible.

## Implementation Results

- Feature-specific backend tests: PASS on Java 21.
- Frontend TypeScript typecheck: PASS on Node 22.
- Frontend production build: PASS on Node 22; existing large-chunk warning remains informational.
- Full backend suite: 384 tests executed; all B4 and FMP/Yahoo wrapper tests pass. One pre-existing unrelated failure remains in `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable`.
- Backend Java 21 package: PASS.
- `git diff --check`: PASS.
