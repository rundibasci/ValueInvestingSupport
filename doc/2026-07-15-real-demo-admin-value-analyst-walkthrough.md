# Real Demo ADMIN and Value-Investment-Analyst Walkthrough

Date: 2026-07-15  
Environment: `realDemo`, frontend `http://localhost:5173`, backend `http://localhost:8080`  
Role: `ADMIN` (`admin@realdemo.local`)  
Method: Chrome headless render, authenticated REST walkthrough, read-only PostgreSQL verification, and reversible CRUD exercises.

## Executive Summary

The clean PostgreSQL/Redis stack started successfully. The startup job completed with `SUCCESS` at 05:14:09 UTC and reported 67 processed records. Of the 64 requested symbols, 61 were persisted and active. Three failed during seed: APD and WBA had no applicable valuation model, while the provider returned `NOT_FOUND` for BROWN.

All discoverable frontend routes and their meaningful ADMIN controls were mapped. The login page was rendered in real Chrome; authenticated functionality was exercised through the same backend APIs used by the frontend because the repository's Playwright runtime requires Node.js, which is unavailable on the host. Every requested symbol was then checked against the 13 authoritative sources required by `$value-investment-analyst`.

One application defect was found: SJM growth calculation throws a server-side `NumberFormatException`, which also breaks its aggregate company review. See the separate bug report.

## Environment and Startup Evidence

| Component | Result | Evidence |
|---|---:|---|
| PostgreSQL | PASS | Docker health `healthy`; clean schema migrated through Flyway V21 |
| Redis | PASS | Docker health `healthy` |
| Backend | PASS with provider limitation | Application running on 8080; startup job `SUCCESS`, 67 records processed |
| Frontend | PASS | HTTP 200 on 5173; Chrome rendered login at 1440x1000 |
| External market data | DEGRADED | FMP reports `PLAN_RESTRICTION`; seeded-security fallback remains available |

The health degradation is a provider/plan limitation, not evidence of a PostgreSQL, Redis, or application-startup failure.

## Seed Coverage

| Status | Count | Symbols |
|---|---:|---|
| Persisted and analyzable | 61 | ABBV, ABM, ADM, ADP, AFL, AOS, BDX, BEN, BRO, CAH, CAT, CB, CHD, CHRW, CINF, CL, CLX, CTAS, CVX, DOV, ECL, ED, EMR, ESS, EXPD, FAST, GD, GPC, GWW, HRL, IBM, INGR, ITW, JNJ, KMB, KO, LIN, LOW, MCD, MDT, MKC, MMM, NDSN, NEE, O, PEP, PG, PH, PNR, PPG, SHW, SJM, SPGI, SWK, SYK, TGT, TROW, VFC, WMT, WST, XOM |
| Seed unavailable | 3 | APD: no valuation model applicable; BROWN: provider `NOT_FOUND`; WBA: no valuation model applicable |

For each of the 64 requested symbols, the walkthrough called profile, company review, valuation, financials, ratios, growth, dividends, score, moat, capital allocation, valuation bands, data verification, and valuation confidence. All 13 sources returned HTTP 200 for 60 persisted symbols. SJM returned HTTP 500 only for growth and company review; its other 11 sources returned 200. APD, BROWN, and WBA returned 404 for the 11 security-backed sources, while the two professional diagnostics returned 200 with unavailable-data semantics.

## Frontend Route and Function Coverage

| Route | Main functions exercised | Result |
|---|---|---:|
| `/login` | Real Chrome render, local credential path, disabled Google-provider state | PASS |
| `/` | Protected dashboard shell and summary data dependencies | PASS via authenticated dependencies |
| `/account` | Account role/email, password capability, Google-link state | PASS |
| `/audit` | Research-decision audit list | PASS |
| `/checklists` | List, create, edit, evaluate against KO, delete | PASS |
| `/screener` | Presets, sectors, exchanges, sort/page request, conservative diagnostics | PASS |
| `/securities/KO` | Profile, prices, financials, ratios, growth, dividends, insiders, peers, valuation and risk panels | PASS |
| `/securities/KO/review` | Aggregate review packet and professional verification/confidence | PASS |
| `/portfolio` | List, create, holding add/update/remove, detail, analytics, simulation validation, rebalance validation | PASS with documented empty-candidate outcomes |
| `/watchlist` | List, add KO, update thresholds/rationale/reason, alerts, remove | PASS |
| `/seed` | Seed-universe API availability and existing startup seed evidence | PASS; destructive reseed not repeated |
| `/universe-curation` | Templates and defensive-universe preview, seeded fallback under FMP restriction | PASS |
| `/admin/seed` | ADMIN seed capability and completed requested startup universe | PASS; duplicate network-heavy seed not repeated |
| `/admin/jobs` | Seven definitions, monitor, cron update/restore, enabled update/restore, disabled manual run, history/events | PASS |
| `/admin/users` | ADMIN-only create capability and request validation mapped | BLOCKED from mutation: creating a user has no delete/disable counterpart and would leave irreversible test data |
| `/auth/oauth2/callback` | Provider configuration inspected | BLOCKED by design: Google OAuth is disabled in realDemo |
| unknown route | React wildcard redirects to `/` | Confirmed from route configuration |

The frontend has no `/api/v1/dashboard` aggregate endpoint; the dashboard composes other APIs. A direct 404 for that invented endpoint is therefore not a product failure. Similarly, `/api/v1/admin/users` intentionally supports POST only, so GET 405 is expected.

## API and Control Evidence

Core read paths returned 200: ping, ADMIN ping, account, OAuth providers, availability diagnostics, all screener metadata/workflows, search, security data panels, professional diagnostics, watchlist, portfolios, checklists, audit decisions, competence preferences, advisor acknowledgement, ADMIN job list/monitor, universe templates, and universe preview.

Reversible mutation evidence:

- Watchlist: create 201, update 200, delete 204.
- Checklist: create 201, update 200, KO evaluation 201 with `MANUAL_REQUIRED`, delete 204.
- Competence preferences: update 200 and restore to empty 200.
- Portfolio: create 201; KO holding create 201, update 200 and delete 204; detail and analytics 200.
- Advisor acknowledgement: update 200 for session `admin-walkthrough`.
- Job cron: changed from `0 0 2 * * *` to `0 5 2 * * *`, then restored.
- Job enabled state: requested and restored; the effective response remains disabled while the global job master switch is off in realDemo.
- Disabled manual job run: correctly recorded `SKIPPED`; history returned the run and events were empty as expected.

Simulation and simulation-based rebalance returned 422 `No eligible watchlist candidates` after the temporary watchlist item had been removed. Explicit rebalance correctly rejected an unpriced/empty portfolio. These are domain validations, not server defects.

## Value-Investment-Analyst Platform Facts (KO Sample)

KO was used as the full-depth navigation sample. These are platform facts, not independently estimated values.

| Fact | Platform value |
|---|---:|
| Data as of | 2026-07-15 |
| Current price | 83.08 |
| Composite fair value | 19.82 |
| Platform margin of safety | -319.17% |
| DCF low / base / high | 12.07 / 18.37 / 23.25 |
| Graham Number | 23.31 |
| EPV fair value | 18.59 |
| DDM | Unavailable from the platform |
| DCF terminal-value share | 56.90% (`highTerminalDependence=false`) |
| Value score | 40.00 |
| Quality / growth / safety / dividend / MoS components | 18 / 5 / 7 / 10 / 0 |
| Valuation confidence | HIGH overall; DCF spread factor LOW |

### Interpretation

The three available valuation anchors (DCF base, Graham Number, and EPV) are directionally consistent and all materially below the recorded market price. The zero MoS component explains an important part of the modest total score; the dividend component does not compensate for the absence of a platform-supported margin of safety. The wide DCF scenario spread lowers confidence in a single point estimate even though historical coverage, model count, completeness, and recent earnings consistency are rated high by the platform.

### Investment Thesis and Verdict

For this sampled platform state, the evidence supports `WATCHLIST`, with Medium confidence. The reason to monitor is the platform's positive quality/dividend evidence and strong data coverage. The reason to wait is that every available valuation anchor indicates no margin of safety. A stronger conclusion would require qualitative management, competitive-position, customer-concentration and regulatory evidence that is unavailable from the platform, plus resolution of the DCF spread uncertainty.

This sample verdict is not mechanically derived from the platform recommendation and is not generalized to the other 63 requested tickers.

## Data Gaps and Limitations

- Browser automation beyond the real-Chrome login render was blocked by the absent Node.js/Playwright runtime. Authenticated functions were tested through their production APIs and route configuration was inspected.
- The provider plan restriction forced universe preview to use seeded-security fallback.
- APD, BROWN and WBA cannot be analyzed from platform security facts after failed seed; no external figures were substituted.
- SJM has an application error affecting growth and aggregate review.
- No custom DCF was run because the user supplied no assumptions and the skill forbids silently inventing them.
- ADMIN user creation was not performed because there is no product cleanup operation.

## Cleanup

Temporary watchlist and checklist data was deleted; competence preferences and job cron/enabled settings were restored. The temporary holding was deleted. Because the product has no portfolio-delete operation, the temporary QA portfolio was removed directly from the clean demo database after functional verification.

### DL1 Follow-up

After this walkthrough, DL1 Portfolio Lifecycle Completion added ownership-scoped `DELETE /api/v1/portfolios/{id}` support and a named confirmation action to the portfolio page. Portfolio-scoped holdings, rebalance proposals/lines, and analytics snapshots are removed through the existing cascade rules, while immutable research-decision audit evidence and shared market data remain intact. Future QA portfolio cleanup can therefore be completed through the product without direct PostgreSQL access.

## Disclaimer

Value Investing Support provides decision support and does not provide personalized investment advice.
