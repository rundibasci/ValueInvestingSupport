# FI2 — Validation Report

Date: 2026-07-16

## Completed Checks

- `backend/mvnw -q -f backend/pom.xml -Dtest=PortfolioImportReportWriterTest,PortfolioCsvParserTest,IsinValidatorTest test` — passed.
- `backend/mvnw -q -f backend/pom.xml -DskipTests package` — passed, including test-source compilation.
- Native esbuild bundle of `PortfolioImportPanel.tsx` — passed.
- Native esbuild bundle of `PortfolioPage.tsx` with the integrated import workflow — passed.
- `git diff --check` — passed.

## Implemented Evidence

- Added owner-scoped paginated import history with optional portfolio/status filtering.
- Added owner-scoped persisted import detail retrieval.
- Added authenticated UTF-8 reconciliation CSV download with RFC 4180 quoting, spreadsheet-formula neutralization, normalized source values, row outcomes, and totals.
- Added security UUID to the existing search response so FI1 explicit mapping requests can reference a security safely.
- Added strict frontend import, row, mapping, history, and security-search types.
- Added multipart preview upload without a manually supplied boundary.
- Added portfolio-page import setup with drag/drop/file selection, existing/new target, base currency, MERGE default, and destructive REPLACE option.
- Added complete row preview, native/base totals, warnings/errors, cash classification, explicit skips, and explicit ISIN mapping through shared-universe search.
- Added commit eligibility, preview expiry handling, idempotent result behavior, second REPLACE confirmation, query invalidation, and target portfolio selection.
- Added recent import history, retained-detail reopening, report download, and explicit FI3 deferral/no-trade/decision-support copy.

## Environment Limitations

- `npm run typecheck` could not run because neither Node nor npm is available on the host PATH.
- The checked-in `node_modules` contains a native esbuild executable, so TSX parsing, bundling, imports, and browser-target syntax were verified without Node.
- `docker compose build frontend` could not run because the Docker daemon is stopped.
- Existing Mockito/Spring backend suites remain constrained by the host's Java 26-only environment; the repository targets Java 21 and Byte Buddy cannot initialize its inline mock maker here.

## Remaining Merge-Gate Checks (as of 2026-07-16)

- Run `npm run typecheck`, `npm run build`, and frontend component tests in the supported Node environment.
- Run FI2 controller/service authorization and PostgreSQL repository pagination tests under Java 21.
- Run the complete backend suite and Testcontainers migration tests under Java 21 with Docker available.
- Perform the supplied-file authenticated browser walkthrough against a disposable portfolio, including mapping, skip, repeated MERGE, confirmed REPLACE, history refresh, and report download.
- Verify keyboard/focus behavior and mobile layout in a real browser.

## Re-Verification — 2026-07-17

The 2026-07-16 report above was written under host constraints (no Node/npm on PATH, Docker daemon stopped, only a Java 26 JDK installed where the project targets Java 21 and Mockito's inline mock maker cannot attach). Those constraints have been resolved for this run: a local Temurin 21 JDK was installed project-side (no system Java changed), Node v24.18.0/npm 11.16.0 were available via the existing `nvm` installation, and Docker Desktop was started.

### Completed Checks

- `JAVA_HOME=<local-temurin-21> ./mvnw test` (full backend suite, no `-Dtest` filter) — **410/410 tests passed.**
- A real FMP API key was configured locally (`.env` and `backend/src/test/resources/application-fmpkey.yml`, both gitignored) after the checks below were first run. `JAVA_HOME=<local-temurin-21> ./mvnw test -Pintegration-test` (Testcontainers PostgreSQL + Docker Redis via `docker-compose.demo.yml`) — **53/53 tests passed** with the real key in place, including `PipelineDemoIT`, `FmpMarketDataClientLiveIT`, `ValuationDemoIT`, and `PortfolioIT`.
- `npm run typecheck` (frontend, strict `tsc -b`) — **PASS**, no errors.
- `npm run build` (`tsc -b && vite build`) — **PASS**, production bundle built successfully.
- `git diff --check` — PASS.

### Bugs Found and Fixed During Re-Verification

- `UniverseSelectionServiceTest.preview_fallsBackToSeededSecuritiesWhenFmpStockListIsUnavailable` (Group SC, unrelated to FI2) failed under a real test run: the test applied a `volumeMin` filter but never stubbed `PriceQuoteRepository` for the fallback security, so the (correct, intentional) "numeric filters require known volume" rule filtered it out. Fixed the test to stub a known volume; not a service bug.
- Flyway migration `db/migration-h2/V11__watchlist_rationale.sql` used PostgreSQL-style multi-column `ALTER TABLE ... ADD COLUMN a, ADD COLUMN b` syntax, which H2 2.3.x rejects. This broke `LocalStackDemoIT` (Group LS, unrelated to FI2) and would break the local H2 demo profile for anyone who exercised it. Split into two single-column `ALTER TABLE` statements, matching the existing convention already used in `V20__ratio_liquidity_coverage.sql`. Fixed and verified.
- `FmpMarketDataClientLiveIT.listSymbols_eitherReturnsNasdaqListOrServiceUnavailable` only tolerated a thrown `MarketDataException` as the "plan-restricted" outcome, but `FmpMarketDataClient.listSymbols` (by design, matching `UniverseSelectionService`'s own fallback handling) returns an empty list rather than throwing when the bulk `/stock-list` endpoint isn't available on the configured plan. Extended the test to accept the empty-list outcome too; confirmed with a real (non-premium) FMP key.
- **Systemic entity/schema drift (found via `PipelineDemoIT`/`ValuationDemoIT` teardown failures once a real FMP key made the full seed → score → risk/moat analysis pipeline actually execute):** 15 JPA entities that reference `security_id` had `ON DELETE CASCADE` declared in their Flyway migration (every single `security_id` FK in the schema does) but were missing the corresponding Hibernate `@OnDelete(action = OnDeleteAction.CASCADE)` mapping annotation — `AltmanResult`, `CapitalAllocationResult`, `CyclicalityResult`, `DividendRecord`, `EarningsQualityResult`, `FundamentalSnapshot`, `InsiderTrade`, `MoatResult`, `PiotroskiResult`, `PriceQuote`, `RatioSnapshot`, `StabilityResult`, `ValuationBandResult`, `ValuationResult`, `ValueScore`. This had zero effect on production Postgres (Flyway's DDL already cascades regardless of the annotation) but broke any H2 `ddl-auto: create-drop` test profile that deletes a `Security` row with dependent analytical data — exactly what `PipelineDemoIT`/`ValuationDemoIT` do in their `@AfterEach` cleanup, and the reason those tests never passed even before the FMP key was missing. Added the missing annotation to all 15, matching the pattern already correctly used by `GrahamChecklistItem`/`WaccResultEntity`. Deliberately left `ChecklistEvaluation` and `ResearchSnapshot` unchanged — their migrations intentionally omit `ON DELETE CASCADE` so audit/research records aren't silently destroyed if a security is later removed.

### Outstanding Gaps (not fixed, flagged for follow-up)

- **No frontend test runner exists.** FI2's own `requirements.md` Decision #10 called for adding Vitest/React Testing Library/user-event "if the frontend lacks a test runner" — it was absent and no test infrastructure or component tests were ever added. `npm run typecheck`/`build` passing does not substitute for the FI2 frontend test matrix in `validation.md` (multipart, mapping, skip, confirmation, invalidation, history, download). This is a real scope gap in FI2, not an environment limitation, and needs its own follow-up (setting up the test runner plus writing the FI2 test matrix is a substantial task in its own right).
- The supplied-file authenticated browser walkthrough (manual review steps in `validation.md`) still has not been performed — it requires a running application instance and a human/browser-driven session, which is outside what this re-verification pass covers.
- Keyboard/focus/mobile-layout verification in a real browser has not been performed for the same reason.
