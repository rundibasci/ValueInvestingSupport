# FI1 — Validation Report

Date: 2026-07-16

## Completed Checks

- `./mvnw -q -Dtest=PortfolioCsvParserTest,IsinValidatorTest test` — passed.
- `./mvnw -q -DskipTests package` — passed.
- `git diff --check` — passed.
- Local, non-committed validation against `/Users/marcello.mazzoni/Downloads/Portfolio.csv` — passed:
  - 15 data rows retained;
  - 2 cash rows classified as EUR/USD cash;
  - 13 security rows retained with valid ISINs;
  - no row classified `INVALID`;
  - quoted decimal-comma values parsed exactly.
- The attached-file validation also confirmed the broker export's header/data mismatch: row currency is in column 5 under `Valore`, while native value is in the unnamed column 6. Parser, roadmap, requirements, plan, and validation text were corrected consistently.

## Implementation Evidence

- Added nullable unique ISIN support on shared `Security` and exact repository lookup.
- Added first-class portfolio cash balances keyed by portfolio/currency.
- Added durable user-owned import previews and normalized row provenance without raw-file retention.
- Added multipart preview and commit endpoints.
- Added explicit mapping conflict checks, skip handling, preview expiry, file/row bounds, filename sanitization, and ownership-safe lookups.
- Added synchronized/idempotent `MERGE` behavior and confirmed transactional `REPLACE` behavior in the service implementation.
- Added scheduled cleanup for expired uncommitted previews.

## Environment Limitation

The existing Mockito/Spring portfolio tests could not execute on the available Oracle Java 26 runtime. Mockito's inline Byte Buddy mock maker fails before test methods run because the JDK does not provide a working self-attachment path in this environment. Retrying with `-Djdk.attach.allowAttachSelf=true` produced the same infrastructure failure. The repository specifies Java 21, but only Java 26 is installed.

This is not an assertion failure in FI1 or the existing portfolio tests. The pure FI1 tests, compilation, and packaging succeed. Before merge, rerun the existing Mockito-based portfolio/controller/repository suites and FI1 integration coverage under Java 21, including PostgreSQL Flyway migration and transaction tests.

## Remaining Merge-Gate Checks

- Run service/controller authorization, preview-purity, mapping-conflict, skip, expiry, idempotent merge, and atomic replace integration tests under Java 21.
- Run PostgreSQL/Testcontainers migration coverage for nullable unique ISIN, cash uniqueness, import constraints, concurrent commit protection, and rollback.
- Run the complete backend suite under Java 21.
- Perform an authenticated HTTP preview/commit walkthrough against a disposable local portfolio.
