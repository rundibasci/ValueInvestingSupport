# FI1 — Validation

## Acceptance Criteria

- [ ] The supplied CSV can be uploaded and previewed without changing a portfolio, holding, cash balance, or security mapping.
- [ ] UTF-8 BOM, accented Italian headers, the blank currency header, quoted decimal commas, and blank cells parse deterministically.
- [ ] Quantities, prices, native values, and EUR values use `BigDecimal` and reconcile without binary floating-point error.
- [ ] Valid coded positions resolve by normalized ISIN only; unresolved positions are visible as `NEEDS_MAPPING` and are never guessed from product name.
- [ ] Approved explicit mappings cannot steal an ISIN from another security.
- [ ] EUR and USD cash rows become separate portfolio cash balances and never fake securities/holdings.
- [ ] One malformed row does not hide valid rows, and every skipped/invalid row remains visible in preview and commit results.
- [ ] Preview is immutable, expiring, checksum-bound, user-owned, and tied to one target/new-portfolio decision.
- [ ] `MERGE` synchronizes imported positions and replaying the same file does not double quantities or cash.
- [ ] Duplicate holding and cash rows consolidate deterministically with source-row traceability.
- [ ] `REPLACE` requires explicit preview-bound confirmation and rolls back completely on any write failure.
- [ ] Foreign portfolio/import access returns ownership-safe `404` and causes no mutation.
- [ ] Broker price/value provenance remains distinguishable from cost basis, current platform quotes, fundamentals, valuation, and score.
- [ ] Upload size, row count, schema, content, formula-injection, and malformed-file safeguards produce sanitized responses.
- [ ] Raw uploaded bytes, filesystem paths, secrets, provider payloads, and stack traces do not appear in persistence, logs, or API errors.
- [ ] Existing portfolio CRUD and holding behavior remains backward compatible.

## Parser and Normalization Matrix

| Scenario | Expected result |
|---|---|
| Exact supplied seven-column header | Schema accepted; row data maps column 5 to currency and blank column 6 to native value |
| UTF-8 BOM before `Prodotto` | BOM removed; header recognized |
| `Quantità` accented header | Normalized and mapped without losing semantic validation |
| Quoted `"8779,48"` | Parsed exactly as `8779.48` |
| Integer quantity `570` | Parsed exactly with supported holding scale |
| Empty quantity/price on cash row | Accepted under cash-row rules |
| Empty quantity on coded security | Row `INVALID` with stable reason |
| Extra/missing column | Actionable schema or row-shape error; no mutation |
| Unclosed quote/malformed CSV | Sanitized parsing failure; no preview persisted as ready |
| Oversized bytes/rows | Rejected before portfolio mutation |
| Formula-like product text | Stored safely and never executed/rendered as HTML |
| Invalid/unsupported currency | Row warning or invalid status according to required-field rules |
| Excess numeric precision/range | Explicit bounded validation failure |

## Resolution and Reconciliation Matrix

| Scenario | Expected result |
|---|---|
| Existing exact ISIN | `READY` with the linked symbol/security |
| Lowercase/spaced ISIN input | Normalized, validated, then resolved |
| Invalid ISIN check digit | `INVALID`; no lookup or mapping mutation |
| Valid unknown ISIN | `NEEDS_MAPPING`; company name is not used to guess |
| Explicit mapping to existing security | Assigned only after uniqueness/conflict validation at commit |
| ISIN already belongs to another security | Conflict; commit blocked for that row |
| Two rows resolve to same security | Consolidated decimal-safe position with both source rows retained |
| EUR cash and USD cash | Two cash balances with native/base values retained |
| Duplicate cash currency rows | Consolidated by currency with traceability |
| Native total differs from quantity × last | Warning with source values preserved |
| EUR converted total mismatch | Explicit reconciliation warning; no invented FX rate |

## Commit and Transaction Matrix

| Scenario | Expected result |
|---|---|
| Preview only | No domain/reference mutation |
| MERGE into empty owned portfolio | Holdings and cash created from ready rows |
| Repeat identical MERGE | Same terminal result or safe no-op; no doubled amounts |
| MERGE changed source file | Represented imported positions synchronized; unrelated behavior follows documented merge rules |
| REPLACE without confirmation | Rejected before mutation |
| Confirmed REPLACE | Target holdings/cash atomically match committed rows |
| Failure during REPLACE | Previous holdings/cash remain unchanged |
| Invalid row not skipped | Commit rejected with row reason |
| Invalid row explicitly skipped | Valid rows commit; skipped row retained in report |
| Expired preview | Commit rejected; new preview required |
| Target differs from preview | Commit rejected; new preview required |
| Concurrent identical commit | One effective mutation and one consistent terminal audit result |
| Other user's portfolio/import | Ownership-safe `404`; no information leak |

## Automated Checks

- [ ] CSV parser and localized `BigDecimal` unit tests pass.
- [ ] ISIN syntax/check-digit and conflict-resolution tests pass.
- [ ] Preview/reconciliation/duplicate classification tests pass.
- [ ] Portfolio import controller and service authorization tests pass.
- [ ] MERGE idempotency and REPLACE rollback integration tests pass.
- [ ] PostgreSQL and H2 migration checks pass, including multiple null ISINs and unique non-null ISINs.
- [ ] Existing portfolio CRUD/integration tests pass unchanged.
- [ ] Backend compile/package and full relevant test suite pass.
- [ ] `git diff --check` reports no whitespace errors.

## Manual Validation with the Supplied File

1. Run locally with an authenticated test user and a user-owned empty portfolio.
2. Upload `/Users/marcello.mazzoni/Downloads/Portfolio.csv` to preview; do not copy the source file into Git.
3. Confirm all source rows appear, including ACOMO and both cash rows, with exact normalized decimals and reconciliation totals.
4. Confirm coded holdings resolve only where the database has the ISIN; unresolved rows remain explicit mapping tasks.
5. Confirm preview leaves all portfolio/security tables unchanged.
6. Resolve mappings in a controlled test dataset, commit `MERGE`, and verify quantities, currencies, cash balances, provenance, and audit results.
7. Repeat the commit and verify no quantities or cash balances double.
8. Exercise confirmed `REPLACE` against a disposable owned portfolio and induce a failure to verify rollback.
9. Attempt access from a second user and verify ownership-safe responses.

## Merge Criteria

- [ ] Every acceptance criterion is satisfied or explicitly documented as blocked with owner and follow-up phase.
- [ ] Automated and manual evidence is recorded without committing personal portfolio data or credentials.
- [ ] FI1 does not silently implement FI2 UI or FI3 provider/analysis orchestration.
- [ ] Security, ownership, provenance, data-availability, and decision-support boundaries are reviewed.
- [ ] Database migration rollback/forward behavior and existing-data compatibility are reviewed.
- [ ] No unrelated user changes are included in the FI1 implementation commit.
