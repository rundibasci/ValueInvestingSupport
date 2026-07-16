# FI1 — Portfolio CSV Parsing, Mapping & Import API

## Context

The platform currently requires holdings to be entered one at a time using a ticker. The supplied broker export uses Italian headers and identifies listed positions primarily by ISIN:

`Prodotto,Codice,Quantità,Ultimo,Valore,,Valore in EUR`

It also contains quoted decimal-comma values, EUR and USD positions, a data/header mismatch where currency occurs under `Valore` and native value occurs in the unnamed sixth column, and cash rows without an ISIN. The current `Security` model has no ISIN, `Holding` stores only symbol/quantity/cost/currency, and cash is not represented independently. FI1 must add a safe backend import boundary without folding FI2 UI work or FI3 seed-and-analysis orchestration into this phase.

This phase implements roadmap phase **FI1: CSV Parsing, Mapping & Import API** and follows `specs/mission.md` and `specs/tech-stack.md`: data before opinion, transparent provenance, explainable missing data, user ownership, immutable financial history, cache/provider separation, and no brokerage execution.

## Scope

### Upload and parsing

- Add `POST /api/v1/portfolios/imports/preview` as an authenticated multipart endpoint accepting one CSV file, an optional user-owned target portfolio ID, optional base currency, and import mode.
- Default base currency to `EUR` for the supplied schema and default mode to `MERGE`.
- Parse RFC 4180-compatible comma-delimited CSV with quoted decimal commas, UTF-8 or UTF-8 BOM, localized accented headers, blank cells, and the unnamed sixth column.
- Match supported headers by normalized name and position. For this broker schema, follow observed row semantics: column 5 contains currency and the unnamed column 6 contains native value. Reject ambiguous or materially different schemas with an actionable error.
- Parse financial numbers with explicit locale rules into `BigDecimal`; never use binary floating point for quantities, prices, or values.
- Keep row number and sanitized original field values for reconciliation and diagnostics.

### Security and cash resolution

- Add a nullable, unique, normalized ISIN field to platform-wide `Security`, with repository lookup and validation for the 12-character ISIN shape and check digit.
- Resolve a coded row by exact normalized ISIN. Do not guess from product/company name.
- Return unresolved or ambiguous coded rows as `NEEDS_MAPPING`; FI1 may accept an explicit reviewed ISIN-to-existing-security mapping in the commit request, but interactive search/save UX belongs to FI2.
- Persist an approved mapping by assigning the ISIN to the selected platform security only after uniqueness and conflict checks. Mapping must never silently reassign an ISIN already linked to another security.
- Classify rows beginning with `CASH & CASH FUND` and lacking a code as cash balances, keyed by ISO currency.
- Add a user-owned portfolio cash-balance model because cash must participate in portfolio value without masquerading as a security or holding.

### Preview contract

- Preview performs no portfolio, holding, cash, security-mapping, or shared-universe mutation.
- Persist a short-lived, user-owned import preview so commit is based on the exact reviewed normalized content, not a reparsed client payload.
- Return an `importId`, checksum, detected schema, base currency, import mode, normalized rows, totals, and expiry.
- Each row returns its source row number, product name, source code/ISIN, quantity, source last price, native currency/value, base-currency value, resolved security/symbol when available, classification, status, warnings, and errors.
- Use stable statuses at minimum: `READY`, `CASH`, `NEEDS_MAPPING`, `WARNING`, and `INVALID`.
- Report duplicate resolved holdings, duplicate cash currencies, malformed values, invalid ISINs, unsupported identifiers, missing required fields, currency mismatches, and reconciliation discrepancies without suppressing valid rows.
- Escape or neutralize spreadsheet formula prefixes in any later CSV/report rendering and never render uploaded content as HTML.

### Commit behavior

- Add `POST /api/v1/portfolios/imports/{importId}/commit` for a non-expired preview owned by the authenticated user.
- Commit to either the previewed user-owned portfolio or a newly created user-owned portfolio. A target change after preview requires a new preview.
- Reject commit while invalid or unresolved security rows remain unless the request explicitly marks those rows as skipped; skipped rows remain visible in the final report.
- `MERGE` is the default and must be replay-safe for the same import checksum. It creates missing holdings/cash balances and updates the positions represented by the import without adding quantities again on repeat commit.
- Consolidate duplicate rows for the same resolved security or cash currency using decimal-safe sums, while retaining the source-row relationship in audit details.
- `REPLACE` requires an explicit confirmation token/flag tied to the preview. Validate the entire commit first, then atomically replace holdings and cash balances in only the selected portfolio.
- Record original filename, content checksum, uploader, timestamps, target portfolio, mode, base currency, source and committed row counts, warnings/errors, row outcomes, and source values used for reconciliation.
- Preserve broker last price, native market value, base-currency value, and import timestamp as provenance; do not treat them as current platform quotes, cost basis, valuation, or provider fundamentals.
- Emit existing research decision audit events where portfolio holdings change, with import correlation but without raw file content.

### Authorization, retention, and safety

- `INVESTOR`, `ADVISOR`, and `ADMIN` may import only into their own portfolios. Use ownership-safe `404` behavior for foreign portfolios/imports.
- Import previews, mappings under review, reports, and source values are user-owned; approved ISIN on `Security` is shared reference data.
- Configure maximum upload bytes, maximum rows, preview retention, allowed media types, and bounded parsing resources. Reject oversized, binary, malformed, or archive content before persistence.
- Do not store the raw uploaded file beyond parsing unless encrypted raw-file retention is separately approved. Initial decision: store checksum, filename, normalized values, and audit outcomes only.
- Sanitize client-visible errors and logs. Never expose raw provider payloads, credentials, local paths, stack traces, or another user's data.

## Decisions

1. **FI1 is backend-only.** Drag/drop, preview tables, mapping search, and portfolio-page UX belong to FI2.
2. **FI3 remains separate.** Commit imports positions and cash but does not automatically call market providers, seed securities, or calculate analysis. FI3 will consume the committed import result.
3. **Preview is mandatory.** No upload directly mutates a portfolio.
4. **ISIN is the authoritative import identifier.** Company-name matching is prohibited; reviewed mapping is explicit and conflict checked.
5. **Cash is first-class portfolio state.** It is stored separately by currency and never represented using a fake ticker.
6. **MERGE means position synchronization, not additive buying.** Replaying the same file cannot double quantities.
7. **REPLACE is atomic and explicitly confirmed.** Failure leaves the previous portfolio unchanged.
8. **Broker value is reconciliation evidence.** It is not cost basis or a substitute for current platform market data.
9. **Partial files remain inspectable.** Bad rows do not erase valid preview results, but unresolved rows must be resolved or explicitly skipped before commit.
10. **Raw CSV retention is excluded initially.** Normalized audit records minimize sensitive-data and formula-injection exposure.

## Out of Scope

- FI2 React/static import UI and downloadable reconciliation UX.
- FI3 automatic shared-universe seeding, in-depth review generation, valuation/scoring, and portfolio analytics orchestration.
- Automatic name-to-ticker inference or external ISIN lookup through an unapproved provider.
- Broker/custodian API integration, trade execution, transaction history, tax lots, realized/unrealized P&L, or performance accounting.
- XLS/XLSX, PDFs, semicolon-delimited exports, or arbitrary broker templates in the first increment.
- Automatic FX-rate reconstruction when the source base-currency value is absent.

## Compatibility and Risks

- Adding ISIN must remain nullable for existing securities and unique only when present; H2/PostgreSQL migration behavior must be verified.
- Existing holdings have no import provenance and must remain unchanged until an explicitly confirmed commit targets them.
- Current holdings permit duplicate symbols; FI1 consolidation and idempotency need database or transactional protection against concurrent commits.
- Cash changes portfolio totals and later analytics. FI1 provides persistence and import results; existing portfolio DTO behavior should remain backward compatible until consumers deliberately include cash.
- ISIN identifies an instrument but does not always determine the provider ticker/exchange representation. Unresolved mappings must stay visible rather than guessed.
- The supplied file has no explicit as-of timestamp or acquisition cost. Imported prices/values must be labelled as source-at-import, and `Ultimo` must not populate `averageCostBasis`.
