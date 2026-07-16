# FIH — Implementation Plan

## 1. Assessment

1. Re-read `PortfolioImportService.java`, `PortfolioImportController.java`, and `applyMappings`/`synchronizeHolding`/`commit` against the 2026-07-16 review notes; record current file/line numbers in `assessment-notes.md` (they may have drifted).
2. Query the local/demo database (or write a throwaway repository query) for any `Holding` whose `averageCostBasis` is `null` on a symbol that also has a `PortfolioImport` commit record in `MERGE` mode, to check for already-corrupted data from the pre-fix behavior.
3. Query for any `Security` whose `isin` was set after its creation timestamp by a non-admin-initiated import commit, to check for already-unauthorized bindings under the pre-fix behavior.
4. Write `assessment-notes.md`: confirms both bugs reproduce, states the outcome of steps 2–3 (data note needed or not), and confirms scope before any code changes — this file, not code, is the FIH1 deliverable.

## 2. Cost-Basis Preservation Fix

1. In `PortfolioImportService.synchronizeHolding`, change the unconditional `holding.setAverageCostBasis(null)` so it only applies when `existing.isEmpty()` (a newly created holding) — leave a pre-existing holding's `averageCostBasis` untouched on merge.
2. Add `PortfolioImportServiceTest#synchronizeHolding_preservesExistingCostBasisOnMerge`: seed a holding with a manually-entered `averageCostBasis`, run a `MERGE` commit for the same symbol, assert the value survives.
3. Add `PortfolioImportServiceTest#synchronizeHolding_newHoldingHasNullCostBasis`: commit a symbol with no pre-existing holding, assert `averageCostBasis` is `null` (unchanged default behavior — the CSV format has no cost-basis column).

## 3. ISIN Admin-Approval Gate

1. Add a new row/commit status value alongside the existing ones in the import DTOs (e.g. `NEEDS_ADMIN_MAPPING`) for rows whose mapping would create a new ISIN↔`Security` binding but whose requester is not `ROLE_ADMIN`.
2. In `applyMappings`, before calling `target.setIsin(row.getIsin())`: if `target.getIsin() == null` (a genuinely new binding, not a reassignment — the existing conflict guard already handles reassignment) and the authenticated principal lacks `ROLE_ADMIN`, do not apply the mapping; mark the row `NEEDS_ADMIN_MAPPING` with an actionable message and continue processing the rest of the import.
3. Add `PUT /api/v1/admin/securities/{securityId}/isin` (admin-only) to `SecurityAdminController` or a new small controller in `admin/` — accepts the ISIN, reuses `applyMappings`'s existing reassignment conflict check (extract it to a shared method if not already), returns the updated security. This is the only way an admin can complete a binding a non-admin user flagged.
4. Add `PortfolioImportServiceTest#applyMappings_blocksNewIsinBindingForNonAdmin` and `PortfolioImportServiceTest#applyMappings_allowsNewIsinBindingForAdmin`.
5. Add a controller test for the new admin endpoint: non-admin gets `403`, admin succeeds, conflicting ISIN still rejected.

## 4. Test Coverage — `PortfolioImportService` / `PortfolioImportController`

1. Create `backend/src/test/java/it/mazzoni/vis/portfolio/importing/PortfolioImportServiceTest.java` covering (beyond sections 2–3 above):
   - `commit_mergeMode_consolidatesDuplicatePositions`
   - `commit_replaceMode_requiresExplicitConfirmation`
   - `commit_replaceMode_deletesExistingHoldingsOnlyAfterValidation`
   - `commit_mergeMode_isIdempotentForSameUploadedFile` (re-run the supplied `Portfolio.csv` twice, assert no quantity/cash doubling)
   - `commit_skippedRow_isExcludedButRemainsInResult`
   - `commit_foreignPortfolio_isRejectedWithOwnershipSafe404`
2. Extend `PortfolioImportControllerTest.java` (create if it doesn't already cover commit/preview) for the same scenarios at the HTTP layer: auth required, ownership-safe 404s, request/response shape.
3. Reuse the supplied `Portfolio.csv` fixture path already established in FI1/FI2 tests; do not introduce a second fixture file.
4. Run `./mvnw -Dtest=PortfolioImportServiceTest,PortfolioImportControllerTest test` and confirm green before moving to section 5.

## 5. Performance — N+1 Query Fixes

1. Add `SecurityRepository.findByIsinIn(List<String> isins)` if it doesn't already exist.
2. In `preview()`, replace the per-row `securities.findByIsin(row.getIsin())` call with: collect all row ISINs first, call `findByIsinIn` once, build an in-memory `Map<String, Security>`, then resolve each row from the map.
3. Evaluate `synchronizeHolding()`'s per-symbol-group `holdings.findByPortfolioAndSymbol(...)` calls: if `commit()` already loads all of a portfolio's holdings once elsewhere, reuse that; otherwise add a batched lookup (`findByPortfolioAndSymbolIn`) only if a test with 30+ distinct symbols shows a measurable query-count reduction.
4. Add a regression test asserting the query count for a `preview()` call with N rows does not scale linearly with N (e.g. Hibernate statistics `getQueryExecutionCount()` before/after, or a fixed upper bound assertion) so this can't silently regress.

## 6. Full-Suite Verification (hard merge gate)

1. Run `cd backend && ./mvnw test` (full unit suite) under the local Java 21 setup — must be 100% green, no skips attributed to environment.
2. Run `cd backend && ./mvnw test -Pintegration-test` (Testcontainers PostgreSQL, Docker Redis, real FMP key) — must be 100% green.
3. Run `cd frontend && npm run typecheck && npm run build` — no frontend changes are expected in this phase, but confirm no accidental breakage.
4. Run `git diff --check`.
5. Record actual command output/evidence in `validation.md` before merge — no "blocked by environment" entries permitted for this phase (see Decision 3 in `requirements.md`).

## 7. Optional Cleanup (FIH5, only if it falls out naturally)

1. If writing section 4's tests reveals that `commit()`'s ~45-line, multi-responsibility body is genuinely hard to test in isolation, extract `resolvePortfolio()`, `validateCommitReadiness()`, and `applyHoldingsAndCash()` as named private methods — but only to the extent it measurably simplifies the tests already written, not as a standalone refactor pass.
2. Do not expand this section beyond what section 4 already required — if the tests were straightforward to write against the existing structure, skip this section entirely.
