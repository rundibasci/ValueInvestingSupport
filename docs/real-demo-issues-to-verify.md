# Real-demo issues to verify

Source: [Real-demo admin full walkthrough](./real-demo-admin-full-walkthrough.md)  
Prepared: 2026-07-15  
Configuration: `docker-compose.realDemo.yml`

## Purpose

This register converts the walkthrough findings into independently verifiable issues. An issue is closed only when its acceptance checks pass against a freshly started real-demo stack and the required evidence is attached.

## Priority summary

| ID | Priority | Issue | Primary route | Status |
|---|---|---|---|---|
| RD-V-001 | P0 | Universe Curation returns no candidates | `/universe-curation` | Fixed |
| RD-V-002 | P0 | Numeric universe filters need a non-empty E2E regression | `/universe-curation` | Fixed (API); browser coverage tracked by RD-V-008 |
| RD-V-003 | P1 | Exchange metadata and Screener exchange filter are empty | `/screener` | Fixed |
| RD-V-004 | P1 | Derived analytics are incomplete across the seeded universe | `/screener`, security pages | Fixed |
| RD-V-005 | P1 | Persistent universe exclusions are missing | `/universe-curation` | Confirmed missing feature |
| RD-V-006 | P1 | Security review has material provider/internal data gaps | `/securities/:symbol/review` | Partially fixed |
| RD-V-007 | P2 | Disabled jobs show misleading next-run information | `/admin/jobs` | Fixed |
| RD-V-008 | P1 | Full admin browser interaction pass remains incomplete | All routes | Not a bug — verification gap |
| RD-V-009 | P2 | OAuth cannot be verified in real-demo | `/login`, `/account` | Not a bug — environment-dependent |

## RD-V-001 — Restore a usable Universe Curation result set

**Priority:** P0 — release/demo blocker  
**Related findings:** RD-QA-001, RD-QA-002  
**Affected routes:** `/universe-curation`, `/screener`

### Problem

The default NASDAQ/NYSE criteria return zero candidates because real-demo securities do not contain exchange metadata. Every later filter therefore operates on an empty set.

### Reproduction

1. Start `docker-compose.realDemo.yml` with a fresh or known database.
2. Sign in with the documented real-demo admin account.
3. Open `/universe-curation`.
4. Keep the default exchanges, country, and numeric criteria.
5. Select **Preview universe**.
6. Query `/api/v1/screener/exchanges` and inspect exchange values in Screener rows.

### Expected

- The default preview returns at least one eligible security.
- Seeded securities contain correct exchange values.
- `/api/v1/screener/exchanges` returns the distinct exchanges represented in the universe.
- The preview explains genuine zero-result cases rather than silently masking missing metadata.

### Acceptance checks

- [ ] All 10 default `REAL_DEMO_TICKERS` have a non-null, normalized exchange after startup ingestion.
- [ ] `/api/v1/screener/exchanges` includes at least `NASDAQ` and `NYSE` when those exchanges are represented.
- [ ] Default Universe Curation preview returns a non-empty list.
- [ ] Selecting only NASDAQ and only NYSE produces correctly partitioned results.
- [ ] Unknown exchange data is surfaced as a data-quality condition and is not silently accepted as either exchange.
- [ ] Restarting the stack does not erase or duplicate repaired exchange metadata.

### Required evidence

- Authenticated API responses for exchange metadata and three preview variants.
- Database query showing symbol-to-exchange coverage.
- Browser screenshots of the populated exchange control and non-empty preview.

## RD-V-002 — Verify every numeric Universe Curation filter end to end

**Priority:** P0 — regression coverage  
**Dependency:** RD-V-001  
**Affected route:** `/universe-curation`

### Problem

Market-cap, volume, and maximum-symbol inputs appeared ineffective because the candidate set was already empty. Their behavior must be verified with populated, varied data.

### Test matrix

| Control | Baseline | Changed value | Required observable effect |
|---|---:|---:|---|
| Market cap minimum | $1B | $100B | Match count decreases or remains equal; every returned cap is at least $100B |
| Market cap maximum | Empty | $50B | Every returned cap is at most $50B |
| Minimum volume | Empty | 500,000 | Unknown or lower-volume rows are excluded; every returned known volume meets the threshold |
| Max symbols | 100 | 2 | `returnedCount` is at most 2 while `totalMatches` remains the uncapped total |
| Sort | Cap descending | Cap ascending | Row order reverses consistently for distinct caps |

### Acceptance checks

- [ ] Each number field is serialized with the value shown in the UI.
- [ ] Clearing an optional number sends `null`, not `0` or the previous value.
- [ ] Changing a number and selecting **Preview universe** sends a new request.
- [ ] Market cap is populated directly or derived consistently from latest price × shares.
- [ ] A positive volume threshold does not treat unknown volume as a match.
- [ ] Invalid negative values and `maxSymbols` outside 1–500 are rejected or constrained visibly.
- [ ] A Playwright regression executes every row in the matrix.

### Required evidence

- Captured request/response pairs for the matrix.
- Browser screenshots before and after each changed criterion.
- Automated test output showing all matrix cases pass.

## RD-V-003 — Restore exchange filtering in Screener

**Priority:** P1  
**Dependency:** RD-V-001  
**Affected route:** `/screener`

### Problem

The exchange options endpoint is empty and result rows lack exchange values, making exchange-based screening unavailable.

### Acceptance checks

- [ ] The exchange selector is populated from current reference data.
- [ ] Every seeded real-demo security displays its exchange in the table.
- [ ] Selecting NASDAQ returns only NASDAQ rows.
- [ ] Selecting NYSE returns only NYSE rows.
- [ ] Clearing the filter restores the unfiltered count.
- [ ] Exchange filtering works together with sector, score, Altman, and pagination controls.

### Required evidence

- API counts and symbols for unfiltered, NASDAQ, and NYSE requests.
- Screenshot of the selector and filtered table.

## RD-V-004 — Complete derived analytics for the active universe

**Priority:** P1  
**Affected routes:** `/screener`, `/securities/:symbol`, `/securities/:symbol/review`

### Problem

Some current securities report `MISSING_INTERNAL_COMPUTATION` or null Piotroski, Altman, moat, and shares-trend evidence. Coverage differs between the default real-demo pack and older/fallback rows.

### Acceptance checks

- [ ] Define the active real-demo universe explicitly; stale securities outside it are removed, archived, or clearly labeled.
- [ ] Every active symbol runs the same post-ingestion recomputation pipeline.
- [ ] Every active symbol has a Piotroski result or a precise insufficient-data reason.
- [ ] Every active symbol has an Altman result or a precise insufficient-data reason.
- [ ] Every active symbol has moat and capital-allocation results or precise insufficient-data reasons.
- [ ] `MISSING_INTERNAL_COMPUTATION` is never used when the necessary local inputs exist.
- [ ] Re-running ingestion/recomputation is idempotent and retains only the intended latest result.
- [ ] Screener filters include/exclude unavailable results according to documented semantics.

### Verification sample

Verify all 10 default real-demo tickers plus the previously incomplete examples: INGR, CINF, SJM, CB, ADM, AFL, AOS, TROW, ABBV, and ED, if those symbols remain in the active database.

### Required evidence

- Coverage table by symbol and computation type.
- Database counts grouped by availability status.
- Screener screenshots showing available and legitimately insufficient results.

## RD-V-005 — Implement persistent universe exclusions

**Priority:** P1 — missing feature  
**Affected route:** `/universe-curation`

### Problem

The Restrictions control is disabled because no persistence contract exists. Rejected symbols reappear in later previews.

### Acceptance checks

- [ ] Admin can add a symbol exclusion with a rationale.
- [ ] Exclusions persist across logout, restart, and new preview requests.
- [ ] Excluded symbols do not appear in previews or seed requests unless explicitly restored.
- [ ] Admin can list, search, and remove exclusions.
- [ ] Duplicate exclusions are handled idempotently.
- [ ] Invalid or unknown symbols receive a clear validation response.
- [ ] Exclusion create/remove actions are auditable.
- [ ] The UI shows the number of exclusions applied to a preview.

### Required evidence

- API contract tests and database persistence verification.
- Browser create, preview, restart, restore, and audit trail screenshots.

## RD-V-006 — Improve security-detail and review data coverage

**Priority:** P1  
**Affected routes:** `/securities/:symbol`, `/securities/:symbol/review`

### Problem

AAPL and other real-demo securities have missing exchange/market cap, DDM, analyst estimates, WACC components, insider ownership, and acquisition-spend inputs. Some gaps may be legitimate provider limitations; others are ingestion or computation omissions.

### Acceptance checks

- [ ] Classify every missing field as provider-limited, not applicable, insufficient history, failed ingestion, or missing internal computation.
- [ ] Exchange and market cap are populated for all active symbols.
- [ ] DDM is shown only for eligible dividend payers and has an explicit reason otherwise.
- [ ] Analyst estimates clearly state provider/plan limitations when absent.
- [ ] WACC exposes beta, cost of equity, debt/equity weights, and tax-rate provenance when calculated.
- [ ] Capital-allocation fields show a precise availability message when source data is absent.
- [ ] The UI never presents a blank value where an availability reason exists.
- [ ] Custom DCF continues to return a valid result after coverage changes.

### Required evidence

- Field-level availability matrix for AAPL, MSFT, KO, JNJ, and XOM.
- Detail/review screenshots and corresponding API payloads.

## RD-V-007 — Correct disabled-job schedule presentation

**Priority:** P2  
**Affected route:** `/admin/jobs`

### Problem

Disabled jobs show `SKIPPED` plus past or hypothetical `nextRunAt` timestamps, which can look like an active or failed schedule.

### Acceptance checks

- [ ] Disabled jobs display `Disabled` as state, not an operational error.
- [ ] Active next-run timestamps are hidden for disabled jobs or explicitly labeled hypothetical.
- [ ] Enabling a job produces a future next-run timestamp.
- [ ] Disabling it removes or relabels that timestamp immediately.
- [ ] Job history distinguishes disabled skips, successful runs, provider failures, and internal failures.
- [ ] Refresh and status/enabled filters reflect changes without a page reload.

### Required evidence

- Before/after API payloads and browser screenshots for one safely toggled job in an isolated database.

## RD-V-008 — Complete a literal admin every-click browser pass

**Priority:** P1 — acceptance test gap  
**Affected routes:** All 14 application routes

### Problem

The walkthrough used live authenticated APIs and static/current UI inspection because the host lacked Node, Playwright CLI, and a browser executable. Several mutation and browser-only workflows remain unverified.

### Required browser scenarios

- [ ] Login success, invalid password, session refresh, sign out, and protected-route redirect.
- [ ] Dashboard selectors, links, populated state, empty state, and retry state.
- [ ] Screener presets, every filter, sort, pagination, detail/review links, and reset behavior.
- [ ] Universe Curation templates, every selection and number field, preview, seed preview, and exclusions.
- [ ] CSV seed validation, successful controlled seed, duplicate handling, and partial provider failure.
- [ ] Portfolio create, add/edit/remove holding, simulation, rebalance proposal, acknowledgement, print, and cleanup.
- [ ] Watchlist add/edit/remove, filters, alert acknowledgement, and cleanup.
- [ ] Decision-history symbol/date filters and CSV download contents.
- [ ] Checklist create/edit/evaluate/delete and validation errors.
- [ ] Account preference save/reload and unavailable Google unlink state.
- [ ] Admin jobs filters, history/events, safe enable/disable/run controls, and error handling.
- [ ] Admin user validation, successful creation, duplicate conflict, authorization, and cleanup.
- [ ] Security detail tabs, charts, watchlist action, custom DCF, and error states.
- [ ] Security review links, sources, price windows, weighting sliders, DCF, and portfolio action.
- [ ] Unknown route fallback.

### Acceptance checks

- [ ] Run on a disposable real-demo database with cleanup support.
- [ ] Capture console errors, uncaught page errors, failed requests, screenshots, and trace files.
- [ ] Treat unexpected HTTP 4xx/5xx, disabled controls without explanation, stale results, and silent no-ops as failures.
- [ ] Produce a route/control coverage matrix with no untested interactive control.

## RD-V-009 — Decide and verify real-demo OAuth scope

**Priority:** P2 — environment-dependent  
**Affected routes:** `/login`, `/account`, `/auth/oauth2/callback`

### Problem

Google sign-in is intentionally disabled in the present real-demo environment, so OAuth login, callback, linking, and unlinking are not acceptance-tested.

### Acceptance checks

- [ ] Product owner explicitly marks OAuth in scope or out of scope for real-demo acceptance.
- [ ] If out of scope, the UI continues to explain the limitation and password login remains available.
- [ ] If in scope, configure a non-production OAuth client and verify login, callback failure, account linking, duplicate-email resolution, unlinking, and local-password fallback.
- [ ] No OAuth tokens or client secrets appear in screenshots, logs, reports, or source control.

## Recommended verification order

1. RD-V-001 — repair exchange metadata and unblock Universe Curation.
2. RD-V-002 and RD-V-003 — verify numeric and exchange filtering.
3. RD-V-004 and RD-V-006 — normalize analytics and review-data coverage.
4. RD-V-005 — add persistent exclusions.
5. RD-V-007 — correct job-monitor semantics.
6. RD-V-008 — execute the complete browser acceptance pass.
7. RD-V-009 — close the OAuth scope decision.

## Closure record

Use this table when fixes are ready for verification.

| ID | Fix commit/PR | Verification date | Environment/database | Evidence location | Result | Verified by |
|---|---|---|---|---|---|---|
| RD-V-001 |  |  |  |  | Pending |  |
| RD-V-002 |  |  |  |  | Pending |  |
| RD-V-003 |  |  |  |  | Pending |  |
| RD-V-004 |  |  |  |  | Pending |  |
| RD-V-005 |  |  |  |  | Pending |  |
| RD-V-006 |  |  |  |  | Pending |  |
| RD-V-007 |  |  |  |  | Pending |  |
| RD-V-008 |  |  |  |  | Pending |  |
| RD-V-009 |  |  |  |  | Pending |  |
