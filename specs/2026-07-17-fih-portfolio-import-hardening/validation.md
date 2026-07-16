# FIH — Validation and Merge Criteria

## Functional Acceptance

- [ ] A `MERGE`-mode reimport of a portfolio with a manually-entered `averageCostBasis` no longer clears that value.
- [ ] A brand-new holding created by an import still has `averageCostBasis = null` (unchanged prior behavior — the CSV has no cost-basis column).
- [ ] A non-admin user's import-mapping attempt to bind an ISIN to a `Security` that currently has none is rejected and the row is marked with an actionable `NEEDS_ADMIN_MAPPING` (or equivalent) status; the rest of the import is unaffected.
- [ ] An admin user can complete such a binding via the new `PUT /api/v1/admin/securities/{securityId}/isin` endpoint, subject to the same conflict guard `applyMappings` already enforces.
- [ ] Reassigning an ISIN already bound to a different `Security` is still rejected for everyone, admin or not (existing conflict guard unchanged).
- [ ] A `preview()` call no longer issues one `findByIsin` query per CSV row — verified by a query-count assertion, not just wall-clock time.

## Assessment Evidence (must exist before section-2+ fixes were started)

- [ ] `assessment-notes.md` exists and states whether any pre-existing data needed a note (nulled cost basis from a prior merge, or an unauthorized ISIN binding already present).

## Automated Backend Checks

- [ ] `PortfolioImportServiceTest` exists and covers: cost-basis preservation (new vs. existing holding), the admin-approval ISIN gate (blocked for non-admin, allowed for admin), MERGE consolidation, REPLACE confirmation gating, REPLACE deletion ordering, idempotent re-import, skipped-row handling, and ownership-safe rejection of a foreign portfolio.
- [ ] `PortfolioImportControllerTest` covers the same scenarios at the HTTP layer, plus the new admin ISIN endpoint's authorization (403 for non-admin, success for admin).
- [ ] A dedicated test (Hibernate statistics-based query count, or equivalent) proves `preview()`'s query count does not scale linearly with row count.
- [ ] Existing FI1/FI2/FI3 import, seed, portfolio, and analytics tests remain green — no regressions introduced by the cost-basis or ISIN-gate change.

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

- [ ] `./mvnw test` — 100% pass, no skips.
- [ ] `./mvnw test -Pintegration-test` — 100% pass, no skips, no "blocked by environment" notes.
- [ ] `npm run typecheck` — pass.
- [ ] `npm run build` — pass.
- [ ] `git diff --check` — no whitespace errors.

## Manual Review

1. As a non-admin `INVESTOR`, import a CSV row whose ISIN resolves to a `Security` with no existing ISIN (or construct a fixture where this is true); confirm the row shows `NEEDS_ADMIN_MAPPING` and the rest of the import still commits.
2. As `ADMIN`, call the new endpoint to complete that binding; confirm it succeeds and a subsequent conflicting attempt (different ISIN, same security) is still rejected.
3. Import the supplied `Portfolio.csv` twice in `MERGE` mode against a portfolio with a manually-entered cost basis on one holding; confirm the cost basis is unchanged after both imports and quantities/cash did not double.
4. Confirm the reconciliation report and preview UI (FI2) still render correctly with the new row status — no crash, no "undefined" status label.

## Merge Gate

- [ ] All Functional Acceptance and Assessment Evidence items pass.
- [ ] Full-Suite Hard Gate section is 100% green — this phase does not merge with any test failure or environment-limitation caveat.
- [ ] `assessment-notes.md`, `plan.md`, `requirements.md`, and this `validation.md` (updated with actual command output/evidence) all exist in this spec directory.
- [ ] No secret, generated build artifact, or unrelated change is included in the diff.
- [ ] Diff is scoped to `PortfolioImportService`/`PortfolioImportController`, their tests, the new admin ISIN endpoint, and this spec directory — no unrelated refactors beyond the optional FIH5 cleanup in `plan.md` section 7, and only if that section's condition was actually met.
