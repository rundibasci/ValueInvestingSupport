# FI2 — Portfolio Import UI & Validation

## Context

FI1 introduced the authenticated backend workflow for broker CSV imports: multipart preview, persisted normalized rows, ISIN-first resolution, explicit mapping, user-owned portfolio cash balances, idempotent `MERGE`, confirmed `REPLACE`, provenance, and row-level outcomes. The supplied export contains Italian headers, quoted decimal commas, EUR/USD positions, cash rows, and a header/data mismatch where column 5 contains currency while the unnamed column 6 contains native market value.

The React application currently manages portfolios on `PortfolioPage` through `frontend/src/api/portfolio.ts`, TanStack Query, React state, Tailwind CSS, and React Router. Security search already exists and must be reused for explicit mapping. FI2 turns the FI1 API into a safe, understandable workflow; it does not seed providers or calculate the FI3 research packet.

This phase implements roadmap phase **FI2: Portfolio Import UI & Validation** in accordance with `specs/mission.md` and `specs/tech-stack.md`. Portfolio imports remain user-owned decision-support records. Broker values are reconciliation evidence, not platform quotes, cost basis, regulated advice, or proof that a security is suitable.

## Scope

### Entry point and import setup

- Add a clearly labelled **Import CSV** action to the portfolio page for authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users.
- Open an accessible step-based import panel or dialog without replacing the existing create/edit/simulate portfolio workflow.
- Step 1 captures:
  - CSV file through drag/drop or native file picker;
  - target choice: an existing user-owned portfolio or a new portfolio name;
  - base currency, defaulting to `EUR` for the supplied schema;
  - mode, defaulting to `MERGE` with `REPLACE` visually separated as destructive.
- Before upload, show the exact supported header and explain that this broker export places currency under `Valore` and native value in the unnamed following column.
- Explain quoted decimal-comma support, maximum file/row limits returned or documented by FI1, accepted file type, preview expiry, and that preview changes nothing.
- Validate required file, target/new name, base currency, and mode client-side while treating backend validation as authoritative.

### API client and server-state model

- Extend `frontend/src/api/portfolio.ts` or add a focused portfolio-import API module with strict TypeScript types mirroring FI1 preview/commit rows, summaries, mappings, statuses, modes, and error bodies.
- Send uploads using `FormData`; do not force a JSON `Content-Type` or manually set the multipart boundary.
- Add stable query/mutation keys for preview, commit, import detail/history, and portfolio refresh.
- Preserve the active `importId` during the workflow so mapping and commit always refer to the persisted preview rather than client-reconstructed values.
- Sanitize API error presentation and never display stack traces, local paths, raw provider payloads, or secrets.

### Preview and reconciliation

- Render every FI1 row in source order with: row number, product, ISIN/source code, normalized quantity, broker last price, native currency/value, base-currency value, resolved symbol/security, classification, status, warning/error, and commit outcome when present.
- Use text plus icons/badges for `READY`, `CASH`, `NEEDS_MAPPING`, `WARNING`, and `INVALID`; color alone is insufficient.
- Show a summary containing source rows, ready positions, cash by currency, native totals by currency, base-currency total, warning/error counts, duplicates that will be consolidated, unresolved mappings, and rows selected for skip.
- Label broker price/value as “source at import” and distinguish it from current platform price/value. Never populate or describe `Ultimo` as average cost basis.
- Keep invalid and warning rows visible. One bad row must not hide valid rows or collapse the preview into a generic failure.
- Allow deliberate skip/unskip of invalid or unresolved rows, with skipped rows retained in confirmation and final reconciliation.

### ISIN mapping

- Show mapping controls only for `NEEDS_MAPPING` security rows; never offer mapping for cash, invalid ISINs, or already resolved rows.
- Reuse `/api/v1/securities/search?q=` and existing symbol/company/profile context to search the shared universe.
- Require the user to explicitly choose one existing security and confirm the displayed ISIN-to-symbol relationship.
- Keep mapping local to the pending commit request until commit succeeds; do not imply that selecting a search result has already changed shared reference data.
- Surface FI1 mapping conflicts clearly, including an ISIN already assigned to another security or a target security already carrying another ISIN.
- Never propose or auto-select a ticker based only on product/company name.

### Commit and destructive confirmation

- Disable commit until every non-skipped row is commit-ready and preview has not expired.
- Show a final confirmation summary of target portfolio, mode, holdings/cash counts, totals, mappings, skipped rows, warnings, and expected mutation.
- For `MERGE`, explain that imported positions are synchronized and replaying the same file does not add quantities again.
- For `REPLACE`, require a second explicit destructive confirmation naming the target portfolio and explaining that its current holdings and cash will be atomically replaced.
- Prevent double submission while commit is pending and handle a replayed committed import as an idempotent result rather than an apparent duplicate action.
- On success, invalidate portfolio list/detail/analytics/preconditions queries, select or open the committed portfolio, and show a stable reconciliation result.
- Do not trigger FI3 seeding or analysis automatically in FI2. A future-facing “Seed and analyze” action may be absent or visibly unavailable until FI3.

### Import history and reconciliation report

- Add the minimal authenticated ownership-safe backend read contracts required by the UI:
  - `GET /api/v1/portfolios/imports` — paginated recent imports owned by the caller, filterable by portfolio/status;
  - `GET /api/v1/portfolios/imports/{importId}` — persisted preview/commit detail for the owner;
  - `GET /api/v1/portfolios/imports/{importId}/report.csv` — downloadable reconciliation CSV for the owner.
- History entries show filename, target portfolio, mode, status, base currency, row/warning/error counts, created/expiry/commit timestamps, and checksum abbreviation.
- The report contains normalized and committed row outcomes, broker source values, resolved symbol, warnings/errors, and totals; it excludes raw uploaded bytes and private/internal exception detail.
- Generate report cells with RFC 4180 quoting, UTF-8, spreadsheet-formula neutralization, and safe `Content-Disposition` filename handling.
- Expired uncommitted previews may disappear under FI1 retention. Committed audit/history remains bounded and accessible according to the backend retention policy.

### Accessibility, responsiveness, and resilience

- Support keyboard-only file selection, dialog/panel navigation, mapping, skip controls, confirmation, retry, close, and report download.
- Use associated labels, visible focus, semantic table headings, `role="status"`/`role="alert"`, and an announced step/progress state.
- On narrow screens, keep row identity/status visible using responsive cards or controlled horizontal scrolling with accessible labels.
- Provide distinct empty, uploading, parsing, preview-ready, mapping-required, commit-pending, committed, expired, partial-error, ownership-safe not-found, and retryable network states.
- Closing or navigating away during preview must not commit. Returning through history may reopen a still-retained owned import.

## Decisions

1. **Integrated portfolio workflow.** FI2 adds a step-based import surface to `PortfolioPage`; it does not introduce a disconnected administration page.
2. **MERGE remains the default.** `REPLACE` is a destructive alternative with a second explicit confirmation.
3. **Backend preview is authoritative.** The browser may validate obvious setup errors but does not duplicate financial parsing, ISIN validation, reconciliation, or commit eligibility logic.
4. **No automatic name mapping.** Search is user-initiated and only explicit selection creates a pending mapping.
5. **Skips are explicit and durable in the result.** The UI never silently omits invalid or unresolved rows.
6. **Import history needs server reads.** Minimal list/detail/report endpoints are in FI2 because persisted reconciliation must survive refresh and cannot rely solely on mutation response memory.
7. **Server-generated CSV report.** This preserves authoritative normalized values/outcomes and centralizes formula-injection and ownership safeguards.
8. **FI3 is not auto-started.** Import success leads to the portfolio and reconciliation result; provider seeding and full analysis remain a separate phase.
9. **Broker values remain provenance.** The UI never presents source last price/value as platform market data, cost basis, fair value, or recommendation.
10. **Add focused frontend testing infrastructure if absent.** Use Vitest, React Testing Library, and user-event consistent with Vite/React; do not add a second application framework.

## Out of Scope

- FI3 shared-universe seeding, asynchronous analysis runs, valuation/scoring refresh, Security Review generation, and portfolio intelligence orchestration.
- Additional broker schemas, XLS/XLSX/PDF, semicolon-delimited files, arbitrary column mapping, or automatic schema learning.
- External ISIN lookup, name-to-ticker inference, or automatic creation of securities from broker text.
- Brokerage connection, trade/order execution, transaction history, tax lots, cost-basis reconstruction, or portfolio P&L/performance accounting.
- Editing parsed quantities, prices, or values in the browser; corrections require a corrected file and a new preview.
- Live FX reconstruction or treating broker EUR conversions as platform FX truth.
- Changing valuation, score, portfolio concentration, or rebalancing formulas.

## Compatibility and Risks

- `PortfolioPage` is already large and feature-rich. The import workflow should be split into focused components/hooks rather than increasing one monolithic render function.
- FI1 currently returns mutation responses but no history/detail/report reads; those endpoints must preserve ownership-safe `404` semantics and avoid exposing another user's filenames or holdings.
- Preview rows can be numerous. Rendering and security search must be bounded/debounced and must not issue one query per row automatically.
- Preview expiry can occur while a user is mapping. The UI must show the expiry time and require a new upload instead of retrying commit indefinitely.
- Existing portfolio queries and selected-portfolio state must update after a new-portfolio import without stale detail or analytics.
- Browser-generated object URLs, if used anywhere, must be revoked. The preferred server report download should stream safely through the authenticated API client.
- Decision-support disclaimers are required wherever imported rows are shown beside fair value, MoS, score, concentration, or recommendation-like platform outputs.
