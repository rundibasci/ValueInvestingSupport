# FIH — Portfolio Import Hardening

## Purpose

Harden the CSV portfolio import feature (Group FI: FI1 API, FI2 UI, FI3 seed/analysis) before more product surface is built on top of it. A code review (`Code Review 2026-07-16` in the project's Obsidian vault) found the import's business-logic class has the weakest test coverage of any comparable class in `portfolio/`, alongside a silent cost-basis data-loss bug and an unauthorized shared-data mutation path. This phase assesses, fixes, tests, and hardens `PortfolioImportService`/`PortfolioImportController` — no new user-facing import features.

## Context

- FI1/FI2/FI3 are merged and, as of the 2026-07-17 re-verification pass (see `specs/2026-07-16-fi2-portfolio-import-ui/validation-report.md`), the full backend suite (410 unit + 53 integration tests) passes green under a working local Java 21 + Docker + FMP-key environment. That re-verification fixed unrelated environment/entity-mapping bugs; it did not touch the import-specific findings below.
- Source findings live in the Obsidian vault at `~/Documents/valueinvestorsupport/ValueInvestingSupport/Code Review 2026-07-16/{Quality,Security,Performance}.md`.
- `PortfolioImportService` (`backend/src/main/java/it/mazzoni/vis/portfolio/importing/PortfolioImportService.java`) owns `preview()`, `applyMappings()`, and `commit()`. It has zero dedicated test coverage today — only `PortfolioCsvParserTest` (parsing) and `PortfolioImportReportWriterTest` (report formatting) exist in `portfolio/importing/`, unlike every comparable business-logic class elsewhere in `portfolio/` (`PortfolioServiceTest`, `PortfolioRebalanceServiceTest`, etc.).
- `Security` rows are shared, platform-wide reference data (screener, valuation, every user's imports). `Holding.averageCostBasis` is a normal user-editable field, set via `AddHoldingRequest`/`UpdateHoldingRequest` and consumed by margin-of-safety/valuation display.

## Scope

### 1. Assessment (must complete before any fix)

- Re-verify each finding below still reproduces against current `main`; record actual current file/line numbers (the review is dated 2026-07-16; line numbers may have drifted).
- Check whether any existing imported portfolio already has a holding whose `averageCostBasis` was silently nulled by a prior `MERGE` reimport, to decide whether a one-time data note (not a silent backfill) is needed alongside the code fix.
- Check whether any `Security` row already has an ISIN set via the unguarded `applyMappings` path with no admin review, to decide whether a data-correction step is needed alongside the code fix.
- Record the assessment outcome in this spec directory (`assessment-notes.md`) before starting section 2.

### 2. Correctness fix — preserve manually-entered cost basis on merge (Quality, Critical)

- `PortfolioImportService.synchronizeHolding` currently calls `holding.setAverageCostBasis(null)` unconditionally on every commit, including when merging into an already-existing holding that has a manually-entered cost basis.
- Only clear `averageCostBasis` when the holding is newly created by this import. An existing holding's manually-entered cost basis must survive a `MERGE`-mode reimport (e.g. refreshing quantities after buying more shares).
- If cost-basis overwrite is ever wanted, it must be an explicit, visible choice surfaced in the preview/commit flow — never a silent default. That explicit-override UI is out of scope for this phase; only stop the silent data loss.

### 3. Security fix — admin-approval gate for new ISIN↔Security bindings (Security, Warning)

- `applyMappings` currently lets any authenticated user permanently set the ISIN on a shared `Security` row via their own import's ambiguous-row mapping, with no ownership or role check. `Security.setIsin` is called from exactly this one place in the whole codebase.
- Existing conflict guards remain: the mapping still cannot silently reassign an ISIN already pointing at a different `Security`.
- New behavior: assigning an ISIN to a `Security` that currently has none (a *new* ISIN↔Security binding) requires the acting user to hold `ROLE_ADMIN`. A non-admin user's mapping for such a row is not applied; the row is marked with a new explicit status (`NEEDS_ADMIN_MAPPING` or equivalent) with an actionable message, and does not block the rest of the import (the row can be skipped like any other unresolved row).
- Since no existing admin workflow can complete such a binding today, add a minimal admin-only endpoint (e.g. `PUT /api/v1/admin/securities/{securityId}/isin`) so an admin actually has a way to finish a binding a user flagged. This endpoint reuses `applyMappings`'s existing conflict guard, it does not duplicate it.
- An admin performing the *same* import-mapping flow is unaffected — the gate only blocks non-admin users from creating *new* bindings, not from mapping already-ISIN'd securities (which was already safe).

### 4. Test coverage — `PortfolioImportService` / `PortfolioImportController` (Quality, Warning)

- Add `PortfolioImportServiceTest` and extend/add `PortfolioImportControllerTest` covering, at minimum:
  - `MERGE` vs `REPLACE` branching; `REPLACE` requires explicit confirmation.
  - Mapping validation (existing ISIN conflict guard, and the new admin-approval gate from section 3).
  - Skipped-row ownership/validation.
  - Duplicate-position consolidation on `MERGE`.
  - Idempotent re-import of the same file (no quantity/cash doubling).
  - The cost-basis preservation fix from section 2 (new-holding vs existing-holding cases).
  - Ownership: a user cannot commit, preview, or view another user's import.
- Reuse the supplied `Portfolio.csv` fixture already established in FI1/FI2's test suite rather than inventing a new one.

### 5. Performance fix — eliminate N+1 query patterns (Performance, Warning)

- `preview()` calls `securities.findByIsin(row.getIsin())` once per CSV row inside its main loop — up to `PortfolioImportProperties.maxRows()` (default 1,000) sequential queries inside one `@Transactional` request. Replace with a single `findByIsinIn(List<String>)` lookup before the loop, resolved from an in-memory map.
- `synchronizeHolding()` calls `holdings.findByPortfolioAndSymbol(...)` once per distinct symbol group during `commit()`. Batch this the same way if a realistic multi-holding fixture (30+ distinct symbols) shows a measurable win; add a regression test/assertion (Hibernate statistics query-count assertion, or a row-count-scaled timing sanity check) so this doesn't silently regress.

## Decisions

1. **This spec bundles FIH1–FIH4 as one initiative**, not four separate branches/specs — the sub-phases are sequential steps of one hardening effort (assess → fix → test → harden), not independently shippable increments like other roadmap groups. FIH5 (optional `commit()` maintainability cleanup) is explicitly deferred and only attempted if it falls out naturally while adding FIH3's tests — it does not gate this phase's merge.
2. **ISIN fix: admin-approval gate, not a per-user alias table.** Keeps `Security` as the single shared source of truth and is a smaller, lower-risk change than introducing a new per-user mapping table. A future phase can revisit the alias-table approach if the admin-approval workflow proves too heavy in practice.
3. **Full local test suite is now a hard merge gate.** As of 2026-07-17 a working local environment exists (project-scoped JDK 21, Docker/Testcontainers, a configured FMP key) and the full suite (410 unit + 53 integration) passes with zero environment-gated exceptions. Unlike FI1–FI3, this phase's `validation.md` does not accept "blocked by environment" as a merge-gate excuse — `./mvnw test` and `./mvnw test -Pintegration-test` must both be run and pass before merge.
4. **No new formulas, no scope creep.** This phase touches only the import service/controller, its tests, and the one new admin endpoint required to make the ISIN fix functionally complete. It does not add new import features, new broker schemas, or new UI beyond what FI2 already shipped.

## Out of Scope

- FIH5 maintainability refactor of `commit()`, unless it falls out naturally from FIH3's test-writing.
- New import-time UI for an explicit cost-basis-override choice (only the silent-default bug is fixed here).
- A full per-user ISIN alias table (rejected in favor of the admin-approval gate; see Decision 2).
- The other three Security/Architecture findings from the 2026-07-16 review that are not import-specific (hardcoded JWT signing key, admin-route authorization gap on bulk seed endpoints, refresh-cookie `Secure` flag, Redis-fallback horizontal-scaling issue) — these belong to a separate hardening group if pursued.
- Any change to the shared valuation/scoring/portfolio-analytics formulas.
