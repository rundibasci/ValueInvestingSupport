# FI1 — Implementation Plan

## 1. Contracts, Configuration, and Schema Decisions

1. Define preview, normalized-row, mapping, commit, row-outcome, reconciliation-summary, and error contracts with stable enums.
2. Add configuration for upload bytes, row limit, preview retention, accepted media types, base currency, and parser limits.
3. Document header normalization, localized-number parsing, ISIN validation, duplicate consolidation, merge synchronization, replace confirmation, and checksum rules.
4. Keep contracts compatible with FI2 UI consumption and FI3 analysis handoff without implementing either later phase.

## 2. Persistence and Flyway Migration

1. Add nullable unique `security.isin` with an index and repository lookup; preserve all existing securities.
2. Add portfolio cash-balance persistence keyed by portfolio and currency.
3. Add import, normalized import-row, source-row relationship, and commit/audit persistence with user ownership, checksum, lifecycle, expiry, mode, totals, safe source values, warnings, and errors.
4. Add uniqueness/idempotency constraints needed to prevent duplicate commits and concurrent position duplication.
5. Verify the migration on PostgreSQL and the repository's H2-compatible test profile.

## 3. CSV Parser and Normalizer

1. Add a maintained CSV parsing dependency only if the current build lacks a safe RFC 4180 parser; do not implement quoted-field parsing by splitting strings.
2. Detect UTF-8 BOM, validate the expected seven-column schema, and normalize accented headers.
3. Map column 5 to native currency and the blank sixth column to native value, then parse quoted decimal-comma quantities/prices/values into `BigDecimal`.
4. Validate row shape, required fields, supported ISO currency, ISIN syntax/check digit, and numeric scale/range.
5. Preserve sanitized source values and generate stable row-level warnings/errors without logging raw rows.
6. Add deterministic unit tests using a sanitized repository fixture matching the supplied CSV structure; do not commit the user's external file without explicit approval.

## 4. ISIN Resolution and Cash Classification

1. Extend `SecurityRepository` with normalized ISIN lookup and conflict-safe assignment support.
2. Resolve coded positions only by ISIN or an explicit reviewed mapping to an existing security.
3. Classify recognized no-code cash products by currency and reject fake-security creation.
4. Detect duplicate security/cash rows and build consolidated preview totals while retaining source-row traceability.
5. Return `NEEDS_MAPPING` for unresolved coded instruments and `INVALID` for malformed identifiers.

## 5. Preview Service and API

1. Implement multipart upload validation before parsing and persistence.
2. Resolve the authenticated user and optional target portfolio with ownership-safe lookup.
3. Compute the file checksum, normalize all rows, resolve securities/cash, reconcile totals, and persist an expiring immutable preview.
4. Return complete valid and invalid row outcomes, summary totals, schema information, checksum, mode, and expiry without mutating portfolio state.
5. Add centralized safe error mapping for invalid schema, oversize input, malformed CSV, and unsupported content.

## 6. Commit Validation and Explicit Mapping

1. Load the preview by import ID and owner; reject expired, foreign, changed-target, already-conflicted, or invalid lifecycle states.
2. Validate requested skips and explicit ISIN-to-security mappings against the preview rows.
3. Persist only conflict-free approved ISIN mappings and record their audit correlation.
4. Rebuild the commit candidate set from persisted normalized rows, not client-supplied quantities or prices.
5. Require every non-skipped row to be commit-ready and validate all mutations before opening the atomic write section.

## 7. MERGE and REPLACE Transactions

1. Implement `MERGE` as synchronization of imported positions/cash by resolved security/currency, never additive quantity replay.
2. Make identical checksum/target/mode commits idempotent and return the original terminal result when safely repeatable.
3. Implement `REPLACE` with preview-bound explicit confirmation and one transaction that leaves prior state intact on failure.
4. Consolidate duplicates with decimal-safe arithmetic and preserve source values separately from `averageCostBasis` and platform quotes.
5. Emit holding audit events and persist per-row terminal outcomes plus reconciliation totals.
6. Protect concurrent commits with database constraints and locking appropriate to PostgreSQL.

## 8. Retention, Security, and Operational Behavior

1. Expire and clean normalized previews according to configuration while retaining bounded commit audit/provenance records.
2. Ensure raw file bytes are discarded after parsing and never copied into logs or error bodies.
3. Sanitize spreadsheet formula prefixes for future report projections and HTML-escape responsibility in API documentation.
4. Add structured logs/metrics for preview/commit counts, duration, terminal status, invalid rows, and conflicts using IDs and counts only.
5. Confirm ownership boundaries for imports, portfolios, rows, cash, and audit reports.

## 9. Verification and Merge Readiness

1. Add parser, ISIN validator/resolver, reconciliation, duplicate, and numeric-boundary unit tests.
2. Add controller/service tests for preview purity, ownership, expiry, explicit skips/mappings, idempotent merge, atomic replace, and sanitized errors.
3. Add PostgreSQL integration coverage for migrations, nullable unique ISIN, locking, uniqueness, and rollback behavior.
4. Run the supplied external CSV manually as local validation without committing it or its path into application configuration.
5. Run targeted tests, backend suite, migration checks, compile/package, and `git diff --check`.
6. Complete validation evidence and review the diff for FI1-only scope before merge.
