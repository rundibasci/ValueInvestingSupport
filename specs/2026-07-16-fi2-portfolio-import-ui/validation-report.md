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

## Remaining Merge-Gate Checks

- Run `npm run typecheck`, `npm run build`, and frontend component tests in the supported Node environment.
- Run FI2 controller/service authorization and PostgreSQL repository pagination tests under Java 21.
- Run the complete backend suite and Testcontainers migration tests under Java 21 with Docker available.
- Perform the supplied-file authenticated browser walkthrough against a disposable portfolio, including mapping, skip, repeated MERGE, confirmed REPLACE, history refresh, and report download.
- Verify keyboard/focus behavior and mobile layout in a real browser.
