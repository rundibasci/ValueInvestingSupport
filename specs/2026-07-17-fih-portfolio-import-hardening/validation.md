# FIH — Validation and Merge Criteria

## Functional Acceptance

- [x] A `MERGE`-mode reimport of a portfolio with a manually-entered `averageCostBasis` no longer clears that value. *(`PortfolioImportServiceTest#synchronizeHolding_preservesExistingCostBasisOnMerge`.)*
- [x] A brand-new holding created by an import still has `averageCostBasis = null` (unchanged prior behavior — the CSV has no cost-basis column). *(`PortfolioImportServiceTest#synchronizeHolding_newHoldingHasNullCostBasis`.)*
- [x] A non-admin user's import-mapping attempt to bind an ISIN to a `Security` that currently has none is rejected and the row is marked with an actionable `NEEDS_ADMIN_MAPPING` status; the rest of the import is unaffected. *(`PortfolioImportServiceTest#applyMappings_blocksNewIsinBindingForNonAdmin`.)*
- [x] An admin user can complete such a binding via the new `PUT /api/v1/admin/securities/{securityId}/isin` endpoint, subject to the same conflict guard `applyMappings` already enforces. *(`AdminSecurityIntegrationTest#setIsin_asAdmin_bindsNewIsin`, `#setIsin_conflictingIsinAlreadyOnAnotherSecurity_returns409`, `#setIsin_targetAlreadyHasDifferentIsin_returns409`; also `PortfolioImportServiceTest#applyMappings_allowsNewIsinBindingForAdmin` for the import-mapping path itself.)*
- [x] Reassigning an ISIN already bound to a different `Security` is still rejected for everyone, admin or not (existing conflict guard unchanged). *(Unconditional guard in `applyMappings`, unchanged; covered by `AdminSecurityIntegrationTest#setIsin_conflictingIsinAlreadyOnAnotherSecurity_returns409`.)*
- [x] A `preview()` call no longer issues one `findByIsin` query per CSV row — verified by a query-count assertion, not just wall-clock time. *(`PortfolioImportServiceTest#preview_batchesIsinLookupsInsteadOfOnePerRow`: 30-row preview asserts `findByIsinIn` called exactly once and `findByIsin` never called.)*

## Assessment Evidence (must exist before section-2+ fixes were started)

- [x] `assessment-notes.md` exists and states whether any pre-existing data needed a note (nulled cost basis from a prior merge, or an unauthorized ISIN binding already present). *(No live/production deployment exists yet; no data-correction step needed — see the file for detail.)*

## Automated Backend Checks

- [x] `PortfolioImportServiceTest` exists and covers: cost-basis preservation (new vs. existing holding), the admin-approval ISIN gate (blocked for non-admin, allowed for admin), MERGE consolidation, REPLACE confirmation gating, REPLACE deletion ordering, idempotent re-import, skipped-row handling, and ownership-safe rejection of a foreign portfolio. *(14 tests, all passing.)*
- [x] `PortfolioImportControllerTest` covers the same scenarios at the HTTP layer, plus the new admin ISIN endpoint's authorization (403 for non-admin, success for admin). *(`PortfolioImportControllerTest`: 7 tests covering preview/commit/history/detail/report and ownership-safe 404 propagation. The admin endpoint's own authorization is covered separately by `AdminSecurityIntegrationTest`, which uses a real Spring Security filter chain rather than a standalone MockMvc controller test — matching this codebase's existing convention, e.g. `AdminUserIntegrationTest`, since `hasRole("ADMIN")` enforcement can't be exercised without the real filter chain.)*
- [x] A dedicated test (Hibernate statistics-based query count, or equivalent) proves `preview()`'s query count does not scale linearly with row count. *(Mockito interaction-count assertion, not Hibernate statistics — equivalent proof at the repository-call boundary: `findByIsinIn` called exactly once regardless of row count, `findByIsin` never called.)*
- [x] Existing FI1/FI2/FI3 import, seed, portfolio, and analytics tests remain green — no regressions introduced by the cost-basis or ISIN-gate change. *(Full suite 436/436 unit + 53/53 integration, see Full-Suite Hard Gate below.)*

## Full-Suite Hard Gate (no environment-limitation exceptions accepted for this phase)

Run against the local project-scoped Java 21 setup established during the 2026-07-17 re-verification (see `specs/2026-07-16-fi2-portfolio-import-ui/validation-report.md` for the exact `JAVA_HOME` pattern), with Docker running and a configured FMP key:

```bash
cd backend
./mvnw test                 # full unit suite — must be 100% green
./mvnw test -Pintegration-test   # Testcontainers PostgreSQL + Docker Redis + live FMP — must be 100% green

cd ../frontend
npm run typecheck
npm run build
```

- [x] `./mvnw test` — 100% pass, no skips. *(436/436, up from the 410-baseline established during FI2/FI3 re-verification; +26 from this phase's new tests.)*
- [x] `./mvnw test -Pintegration-test` — 100% pass, no skips, no "blocked by environment" notes. *(53/53, unchanged count — this phase added no new `@Tag("integration")` tests; `AdminSecurityIntegrationTest` is a plain `@SpringBootTest`, included in the unit-suite run above.)*
- [x] `npm run typecheck` — pass.
- [x] `npm run build` — pass.
- [x] `git diff --check` — no whitespace errors.

## Manual Review

**Not performed as a live browser/human session** — consistent with the honesty standard set during the FI2/FI3 2026-07-17 re-verification, these are recorded as automated-equivalent coverage, not claimed as manual QA:

1. Non-admin blocked from a new ISIN binding, row marked `NEEDS_ADMIN_MAPPING`, rest of import unaffected — covered by `PortfolioImportServiceTest#applyMappings_blocksNewIsinBindingForNonAdmin`, not a live UI session.
2. Admin completes the binding via the new endpoint; conflicting attempt still rejected — covered by `AdminSecurityIntegrationTest` (real HTTP + real Spring Security filter chain + real H2-backed repository), not a live UI session.
3. Re-importing the supplied CSV twice in `MERGE` mode preserves cost basis and doesn't double quantities — covered by `PortfolioImportServiceTest#commit_mergeMode_isIdempotentForSameUploadedFile` and `#synchronizeHolding_preservesExistingCostBasisOnMerge`, using representative fixture data rather than the literal supplied `Portfolio.csv` file end-to-end through the real HTTP/browser stack.
4. FI2 preview UI renders `NEEDS_ADMIN_MAPPING` without crashing — `frontend/src/api/portfolioImport.ts`'s `ImportRowStatus` type and `PortfolioImportPanel.tsx`'s `statusTone`/`canSkip` logic were updated and `npm run typecheck`/`build` pass, but this was not visually confirmed in a running browser.

**Remaining gap for a human before this is fully production-confident:** a real browser walkthrough of the admin-approval flow end-to-end (upload a CSV with an unbound ISIN as a non-admin, see the `NEEDS_ADMIN_MAPPING` row render correctly, have an admin complete the binding, re-open the import and commit it).

## Merge Gate

- [x] All Functional Acceptance and Assessment Evidence items pass.
- [x] Full-Suite Hard Gate section is 100% green — this phase does not merge with any test failure or environment-limitation caveat.
- [x] `assessment-notes.md`, `plan.md`, `requirements.md`, and this `validation.md` (updated with actual command output/evidence) all exist in this spec directory.
- [x] No secret, generated build artifact, or unrelated change is included in the diff.
- [x] Diff is scoped to `PortfolioImportService`/`PortfolioImportController`, their tests, the new admin ISIN endpoint (`AdminSecurityController`, `SecurityIsinService`, DTOs), `SecurityRepository.findByIsinIn`, the `NEEDS_ADMIN_MAPPING` frontend consistency fix, and this spec directory. **FIH5 cleanup was evaluated and skipped** — `commit()`'s existing structure was straightforward to test directly via Mockito without extraction, so the optional refactor's trigger condition in `plan.md` section 7 was never met.
