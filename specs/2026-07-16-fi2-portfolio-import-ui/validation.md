# FI2 — Validation

## Acceptance Criteria

- [ ] An authenticated user can open **Import CSV** from the portfolio page using mouse or keyboard.
- [ ] Setup supports file picker/drag-drop, existing or new portfolio, base currency, `MERGE`, and explicitly destructive `REPLACE`.
- [ ] The supported Italian header, observed currency/native-value column semantics, decimal-comma behavior, limits, and preview-only guarantee are explained before upload.
- [ ] Upload uses multipart `FormData` and the browser supplies the boundary.
- [ ] Every FI1 preview row remains visible in source order with normalized values, classification, resolution, warnings/errors, and text status.
- [ ] Native totals by currency, base-currency total, cash, positions, duplicates, warnings, errors, mappings, and skips reconcile with the FI1 response.
- [ ] Broker last price/value is labelled as source provenance and never displayed as cost basis, platform quote, fair value, or recommendation.
- [ ] `NEEDS_MAPPING` rows can be explicitly mapped through existing security search; no product-name match is guessed or auto-selected.
- [ ] Invalid/unresolved rows can be explicitly skipped, remain visible, and are included in final reconciliation.
- [ ] Commit stays disabled until every non-skipped row is ready and the preview remains valid.
- [ ] `MERGE` explains synchronization/idempotency and repeated import does not double quantities or cash.
- [ ] `REPLACE` requires a second confirmation naming the target and failure leaves its prior state unchanged.
- [ ] Commit success invalidates and refreshes portfolio list/detail/analytics/precondition data and opens the committed portfolio.
- [ ] FI2 does not automatically seed providers or start FI3 analysis.
- [ ] Recent import history and owned import detail survive refresh within retention limits.
- [ ] Reconciliation CSV download is ownership-safe, correctly escaped, formula-neutralized, and contains no raw upload/private exception data.
- [ ] Foreign imports/portfolios are not discoverable and return ownership-safe `404` behavior.
- [ ] Loading, preview, mapping, pending, committed, expired, validation, conflict, partial-error, and retryable network states are distinct.
- [ ] Keyboard, focus, screen-reader announcements, non-color cues, mobile layout, and text zoom are usable.
- [ ] Decision-support and no-trade-execution boundaries remain clear.

## Frontend Test Matrix

| Scenario | Expected result |
|---|---|
| Open import action | Focus enters labelled setup surface; close restores focus |
| No file | Preview disabled with associated validation message |
| Unsupported/oversized file hint | Client warning shown; backend remains authoritative |
| Existing portfolio | Owned target ID included in preview request |
| New portfolio | Name required and sent for commit according to FI1 contract |
| Default setup | Base currency `EUR`, mode `MERGE` |
| Select `REPLACE` | Destructive explanation appears before upload/commit |
| Upload pending | Controls guarded; announced progress state |
| Preview API error | Sanitized actionable error; setup/file can be retried |
| Mixed row statuses | Every row visible with status text/icon and details |
| Wide/mobile preview | Row identity and status remain accessible |
| Cash row | Classified as cash; no mapping action |
| Invalid ISIN | Invalid reason shown; no mapping search until corrected file or skip |
| Unknown valid ISIN | Mapping search available; no automatic selection |
| Search result chosen | Explicit pending mapping shown with ISIN and symbol context |
| Mapping conflict on commit | Affected row highlighted; preview state retained |
| Skip/unskip row | Summary and commit eligibility update; row remains visible |
| Unready non-skipped row | Commit disabled with explanation |
| MERGE confirmation | Synchronization and replay-safe copy shown |
| REPLACE confirmation | Second explicit confirmation names target portfolio |
| Double click commit | One in-flight mutation; no duplicate request |
| Idempotent committed response | Stable success/result, not duplicate holdings warning |
| Preview expired | Commit blocked; user prompted to upload again |
| Commit succeeds | Portfolio/import queries invalidated and target opened |
| Commit fails | Preview/mapping/skip state retained when safe |
| History empty/error/page | Correct empty, retry, and pagination behavior |
| Reopen retained import | Persisted server detail rendered after refresh |
| Report download | Authenticated request, safe filename, feedback, URL cleanup |
| FI3 unavailable | No analysis run starts or is falsely reported |

## Backend Test Matrix

| Scenario | Expected result |
|---|---|
| List own imports | Paginated newest-first owned records only |
| Filter by owned portfolio/status | Correct bounded subset |
| Foreign portfolio filter/import ID | Ownership-safe `404` or empty policy without leakage |
| Import detail before expiry | Full normalized preview returned |
| Committed import detail | Row commit outcomes and timestamps retained |
| Expired uncommitted preview | Documented expired/not-found behavior |
| Report for owned committed import | UTF-8 CSV with rows, totals, mappings, skips, outcomes |
| Commas/quotes/newlines in product | RFC 4180-compliant quoting |
| Formula-prefixed cell | Neutralized in downloadable CSV |
| Filename with path/control characters | Safe `Content-Disposition` filename |
| Foreign report request | Ownership-safe `404`; no filename/content leakage |
| Raw/private data scan | No raw file bytes, stack trace, local path, credential, or provider payload |
| Existing FI1 preview/commit | Contracts and idempotency remain unchanged |

## Supplied CSV End-to-End Walkthrough

1. Sign in as a non-admin investor and open the portfolio page.
2. Open **Import CSV**, select `/Users/marcello.mazzoni/Downloads/Portfolio.csv`, choose a disposable target or new portfolio, and retain `EUR`/`MERGE` defaults.
3. Confirm preview shows 15 rows: 13 coded securities and 2 EUR/USD cash rows, with exact decimal-comma normalization.
4. Verify ACOMO and every other coded row resolves by ISIN or remains an explicit mapping task; no company name is used to guess.
5. Map available unresolved rows through shared security search or deliberately skip them; confirm all skipped rows remain visible.
6. Confirm native totals and `Valore in EUR` base total match the backend response and source file.
7. Commit `MERGE`, open the resulting portfolio, and compare quantities/cash with the reconciliation result.
8. Upload and commit the same file again in `MERGE`; verify quantities and cash do not double.
9. Download the report, reopen the import through history after refresh, and verify outcomes/provenance remain intact.
10. Use a disposable portfolio to test `REPLACE`, including cancellation and a forced failure before a successful confirmed replacement.
11. Sign in as a second user and confirm the first user's imports, filenames, reports, and portfolios are inaccessible.
12. Verify no FI3 seed/analysis run starts during any FI2 step.

## Automated Checks

- [ ] Frontend unit/component tests pass under Vitest/React Testing Library.
- [ ] Multipart, mapping, skip, confirmation, invalidation, history, and download tests pass.
- [ ] Frontend TypeScript strict typecheck passes.
- [ ] Frontend production build passes.
- [ ] FI2 backend history/detail/report tests pass under Java 21.
- [ ] Existing FI1 parser/preview/commit regression tests pass.
- [ ] Portfolio CRUD/detail/analytics/precondition tests pass.
- [ ] PostgreSQL migration and ownership integration tests pass.
- [ ] Accessibility checks report no blocker-level issues.
- [ ] `git diff --check` reports no whitespace errors.

## Merge Criteria

- [ ] Every acceptance criterion is satisfied or documented with severity, owner, and named follow-up phase.
- [ ] Supplied-file walkthrough evidence is recorded without committing personal portfolio data or credentials.
- [ ] FI2 UI and minimal read/report APIs are complete without implementing FI3 orchestration.
- [ ] Ownership, destructive confirmation, provenance, formula-injection, and error-sanitization reviews are complete.
- [ ] Existing portfolio workflows remain usable and regression-tested.
- [ ] Frontend and backend checks pass in the supported Java 21/Node environment.
- [ ] Diff contains only FI2 and necessary supporting test/documentation changes.
