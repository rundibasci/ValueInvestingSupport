# FI2 — Implementation Plan

## 1. Confirm FI1 Contracts and UI Architecture

1. Review FI1 preview/commit DTOs, row statuses, error semantics, expiry, ownership, skip, mapping, merge, and replace behavior against the merged code.
2. Define strict TypeScript import models and discriminated UI states without reimplementing financial parsing in the browser.
3. Split the feature into focused API, hook/state-machine, setup, preview table, mapping control, confirmation, result, and history/report components.
4. Document query keys and transitions from setup → uploading → preview → mapping/skip → confirmation → commit → result.
5. Confirm that FI3 is not triggered or simulated by FI2.

## 2. Backend Import History, Detail, and Report API

1. Add ownership-scoped paginated import history repository/service queries with optional portfolio/status filters and bounded page size.
2. Add owner-only import detail retrieval using the same normalized response mapping as preview/commit.
3. Add an owner-only reconciliation CSV endpoint with safe filename, UTF-8 content type, RFC 4180 quoting, formula neutralization, row outcomes, totals, and provenance labels.
4. Preserve ownership-safe `404`, preview expiry semantics, committed audit retention, and sanitized error responses.
5. Add backend tests for pagination/filtering, foreign-user access, expired preview behavior, report contents/escaping, and absence of raw/private data.

## 3. Frontend API Client and Query Contracts

1. Add TypeScript types for import mode/status, row response, preview, commit, history page/item, mapping, and report download.
2. Add `FormData` preview upload without manually setting multipart boundaries.
3. Add commit, history, detail, and authenticated report-download methods using the existing API client/session behavior.
4. Normalize public API errors into actionable UI messages while retaining stable backend status/reason distinctions.
5. Add shared query keys and invalidation helpers for import and portfolio state.

## 4. Import Entry and Setup Step

1. Add **Import CSV** to the portfolio page with an accessible panel/dialog and step indicator.
2. Build drag/drop and file-picker controls with keyboard support, selected-file summary, remove/replace action, and client-side size/type hints.
3. Add existing/new portfolio choice, owned portfolio selector, new name, base currency, and `MERGE`/`REPLACE` controls.
4. Show exact supported headers, column-5/column-6 semantics, decimal-comma guidance, limits, preview-expiry behavior, and non-mutating preview copy.
5. Validate setup fields and submit the authoritative preview mutation.

## 5. Preview Table and Reconciliation Summary

1. Render all source rows in order with identity, normalized amounts, currency, resolved security/cash classification, warnings/errors, and status text/icons.
2. Add responsive behavior for wide financial data while preserving accessible headings and row identity.
3. Show native totals by currency, base total, positions/cash counts, warnings, errors, unresolved mappings, duplicates, and skips.
4. Label broker source values distinctly from platform quote, cost basis, and valuation fields.
5. Add explicit skip/unskip controls for eligible invalid/unresolved rows and keep skipped rows visible.
6. Display preview ID/checksum abbreviation and expiry time for traceability without exposing internal data.

## 6. Explicit ISIN Mapping Workflow

1. Add a mapping action only to `NEEDS_MAPPING` security rows.
2. Reuse the existing debounced security search API and show symbol, company, exchange, country, and description context where available.
3. Require explicit selection and confirmation of the ISIN-to-security pair; retain it in pending commit state.
4. Support changing/removing a pending mapping before commit.
5. Surface backend mapping conflicts inline and return the user to the affected row without discarding the preview.
6. Ensure no automatic query or selection is based solely on broker product name.

## 7. Confirmation and Commit

1. Derive commit eligibility from authoritative row status plus explicit mapping/skip state.
2. Show a final summary of target, mode, counts, totals, mappings, skips, warnings, and mutation semantics.
3. Add standard `MERGE` confirmation and a second destructive `REPLACE` confirmation naming the target portfolio.
4. Prevent duplicate submissions, preserve the import ID, and handle idempotent committed responses.
5. On success, invalidate portfolio/import queries, select/open the committed portfolio, and render the persisted reconciliation result.
6. On failure, preserve preview, mappings, and skips where safe; distinguish expired, conflict, ownership/not-found, validation, and network failures.

## 8. Import History and Reconciliation Download

1. Add a recent-import section scoped to the current user and optionally the selected portfolio.
2. Display filename, portfolio, mode, lifecycle status, counts, timestamps, and checksum abbreviation with pagination/empty/error states.
3. Allow an owned retained import to reopen in detail/result mode after refresh or navigation.
4. Add authenticated reconciliation report download with pending/error/success feedback and safe object URL cleanup if the client creates one.
5. Make expired uncommitted previews understandable and keep committed outcomes clearly distinct.

## 9. Accessibility, Responsive Behavior, and Decision-Support Copy

1. Verify focus management on open/close, step changes, mapping selection, validation failure, and commit completion.
2. Add semantic labels, table headers, status announcements, alert regions, non-color status cues, and keyboard-operable controls.
3. Verify mobile layout, long product names, many warnings, horizontal data tables, and zoom/text scaling.
4. Add plain-language copy explaining ownership, source provenance, merge/replace behavior, skipped rows, and no trade execution.
5. Preserve the MiFID II decision-support disclaimer wherever portfolio analytical outputs appear beside imported data.

## 10. Automated and Manual Verification

1. Add Vitest/React Testing Library/user-event configuration if the frontend lacks a test runner.
2. Test setup validation, multipart construction, loading/error states, all row statuses, mapping, skips, commit eligibility, replace confirmation, idempotent result, expiry, and query invalidation.
3. Test history/detail/report behaviors, ownership-safe backend responses, CSV escaping/formula neutralization, and download cleanup.
4. Add backend controller/service tests for FI2 read/report endpoints and regression coverage for FI1 preview/commit.
5. Run the supplied CSV end to end against a disposable user-owned portfolio, including repeated `MERGE` and confirmed `REPLACE` on disposable data.
6. Run frontend typecheck/build/tests, targeted and broad backend tests under Java 21, PostgreSQL migration/integration checks, and `git diff --check`.
7. Record validation evidence and review the diff for FI2 scope before merge.
