# Real-demo admin full walkthrough

## Scope and environment

- Date: 2026-07-15 (Europe/Rome)
- Configuration: `docker-compose.realDemo.yml`
- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Role: documented real-demo `ADMIN` account (credentials intentionally omitted)
- Stack state: frontend, backend, PostgreSQL, and Redis running; PostgreSQL healthy
- Evidence method: authenticated admin API requests against the live stack, frontend route/control inspection, and review of the repository's existing Playwright screenshots/report.

Important limitation: a new browser-driven Playwright run could not be launched because this execution host currently has no `node`, `npm`, Playwright CLI, Chromium, or Chrome executable. The existing Playwright artifact is dated 2026-07-03 and used an investor account, so it was used only as supporting visual evidence, not as proof of current admin behavior. Current findings below are based primarily on the live admin APIs and current frontend code. A follow-up browser run is required before calling this a literal every-click acceptance pass.

No external ingestion job, bulk seed, user creation, portfolio trade, destructive delete, or other irreversible/external action was triggered. Secrets and tokens are not included in this report.

## Executive result

The real-demo application is usable for authentication, screening, security research, custom DCF, account display, decision history, checklists, watchlist, portfolios, and admin job monitoring. The principal broken workflow is **Universe Curation**: the default exchange selection excludes every locally available security because exchange metadata is absent. As a result, changing numeric filters appears to have no effect and every tested preview remains empty.

Summary:

- 14 application routes reviewed.
- 13 routes load or redirect as designed.
- 1 core workflow is functionally blocked (`/universe-curation`).
- 3 additional incomplete/misleading areas were found: missing exchange metadata throughout the screener, incomplete analytics on legacy/local symbols, and admin jobs displayed with stale next-run times while disabled.
- 2 deliberately unavailable features are visible: persistent universe exclusions and Google sign-in in real-demo.

## Findings

### RD-QA-001 — Universe Curation always returns an empty selection

| Field | Result |
|---|---|
| Severity | **High** |
| Route | `/universe-curation` |
| Action | Previewed the default criteria, then changed market-cap minimum from $1B to $100B and max symbols from 100 to 2. |
| Expected | Default criteria should return eligible NASDAQ/NYSE US securities; stricter numeric criteria should visibly reduce/change the result. |
| Actual | Both requests returned HTTP 200 with `totalMatches: 0`, `returnedCount: 0`, and an empty `symbols` list. |
| Evidence | Current `/api/v1/screener/exchanges` response is `[]`; all sampled screener rows have `exchange: null`. Default curation criteria always include `NASDAQ` and `NYSE`, so all local rows are excluded before numeric criteria can matter. |
| Impact | An admin cannot preview or seed a criteria-based universe. Numeric inputs appear inert, reproducing the reported behavior. |
| Suggested fix | Populate/derive exchange during ingestion and fallback seeding. Until data is repaired, treat missing exchange as an explicit validation/data-quality error or allow an `Unknown`/all-exchanges option instead of silently returning zero. Add an end-to-end assertion that changing market cap and max symbols changes a non-empty preview. |

### RD-QA-002 — Exchange filtering is unavailable in the Screener

| Field | Result |
|---|---|
| Severity | **Medium** |
| Route | `/screener` |
| Action | Loaded filter metadata and the first 20 results. |
| Expected | Exchange selector contains exchanges for seeded securities, and result rows display their exchange. |
| Actual | Exchange metadata endpoint returns an empty array and sampled results show no exchange. |
| Evidence | HTTP 200 from `/api/v1/screener/exchanges` with `[]`; 65 total screener results, but sampled rows contain `exchange: null`. |
| Impact | Exchange filter cannot be used; this is also the upstream cause of RD-QA-001. |

### RD-QA-003 — Several securities have only partial scoring/quality evidence

| Field | Result |
|---|---|
| Severity | **Medium** |
| Routes | `/screener`, `/securities/:symbol`, `/securities/:symbol/review` |
| Action | Loaded the unfiltered screener and inspected current AAPL detail/review payloads. |
| Expected | Seeded securities expose consistent Piotroski, Altman, moat, capital-allocation, valuation, and profile context. |
| Actual | Newer demo symbols have computed analytics, but several other current rows (for example INGR, CINF, SJM, CB, ADM, AFL, AOS, TROW, ABBV, ED) report `MISSING_INTERNAL_COMPUTATION` for Piotroski/Altman and no moat/share-trend evidence. AAPL still lacks exchange, market cap, DDM value, analyst estimates, and several WACC/capital-allocation inputs. |
| Evidence | Live screener returned 65 results. The SAFE Altman filter works and returned 8 rows, proving Altman is no longer globally unavailable; incompleteness is concentrated in part of the universe. AAPL review returned null exchange/market cap, analyst estimates, DDM, beta/cost-of-equity/capital weights/tax rate, insider ownership, and acquisition-spend-to-FCF. |
| Impact | Sorting/filtering can produce inconsistent comparisons and large portions of an in-depth review show unavailable evidence. |
| Suggested fix | Run the same post-seed scoring/recompute pipeline for fallback/local securities, and expose a clear universe-level coverage indicator before users compare rows. |

### RD-QA-004 — Admin job schedule information is confusing when jobs are disabled

| Field | Result |
|---|---|
| Severity | **Low** |
| Route | `/admin/jobs` |
| Action | Loaded job monitor for all seven jobs. |
| Expected | Disabled jobs clearly show that there is no active next run, or show the next run only as a hypothetical schedule. |
| Actual | Every job is disabled and `SKIPPED`, yet each row still has a `nextRunAt`; several values are already in the past relative to the audit time. Latest error says only that the job is disabled. |
| Evidence | HTTP 200 from `/api/v1/admin/jobs/monitor`; all seven rows have `enabled: false`, current status `SKIPPED`, and zero records on their last successful run. |
| Impact | An admin can mistake stale/hypothetical timestamps for an active schedule and cannot tell whether real-demo data freshness is maintained. |
| Suggested fix | Show `Disabled` instead of a next-run timestamp, label calculated schedule times as hypothetical, and distinguish disabled/skipped state from an operational error. |

### RD-QA-005 — Persistent universe exclusions are not implemented

| Field | Result |
|---|---|
| Severity | **Medium (missing feature)** |
| Route | `/universe-curation` |
| Action | Inspected the Restrictions panel/control. |
| Expected | Admin can persist symbol exclusions so future previews/seeds consistently omit rejected securities. |
| Actual | Control is disabled and the page explicitly says persistence is waiting on a backend contract. |
| Evidence | Disabled `Exclusion persistence unavailable` button in the current frontend. |
| Impact | Curation decisions cannot be retained; admins must repeatedly reconstruct filters and manually review the same symbols. |

### RD-QA-006 — Google sign-in is intentionally unavailable in real-demo

| Field | Result |
|---|---|
| Severity | **Info (environment limitation)** |
| Route | `/login` |
| Action | Inspected authentication choices. |
| Expected | Real-demo clearly indicates which login methods work. |
| Actual | Password login works; Google sign-in is disabled/not configured and the UI explains this. |
| Evidence | Current login implementation disables the Google control when configuration is absent and instructs use of demo email/password. |
| Impact | None for the documented demo path; OAuth cannot be acceptance-tested in this configuration. |

## Route and control walkthrough

| Route/page | Controls/workflows reviewed | Expected and actual result | Status |
|---|---|---|---|
| `/login` | Email/password sign-in, Google option | Documented admin password login succeeds and yields ADMIN authorization. Google is explicitly unavailable in real-demo. | Pass with documented limitation |
| `/` Dashboard | Portfolio selector, links to portfolio/watchlist, retry states | Route is protected and reachable. Fresh admin currently has no portfolios, so portfolio-dependent cards cannot demonstrate populated behavior. | Pass / empty state |
| `/screener` | Presets, sector/exchange and numeric filters, Altman zone, sorting/paging, row detail/review links | Base POST succeeds with 65 results; SAFE filter changes total to 8; custom DCF-linked data is present. Exchange selector is empty and some rows lack computed quality evidence. | Partial; RD-QA-002/003 |
| `/seed` | CSV ticker input, seed button, admin packs, links | Page and controls are wired. Actual bulk seed was not triggered because it can call the external provider and mutate shared reference data. | Not destructively executed |
| `/universe-curation` | Template, sort, exchange/country/sector multiselect, exclusions toggle, four numeric fields, preview, seed preview | Templates load, but both default and strict previews are empty. Seed remains unusable because there is no preview. Persistent exclusions are disabled. | **Fail; RD-QA-001/005** |
| `/portfolio` | Create portfolio, holding inputs/removal, simulation, rebalance, print/advisor controls | API list succeeds and is empty for the admin account. Controls are implemented, but portfolio mutations were not created solely for QA because the UI has no portfolio-delete cleanup flow. | Pass / empty state; mutation not executed |
| `/watchlist` | Add/edit/remove, All/Alerts/Quiet filters, acknowledgement | List and alert endpoints return HTTP 200; admin currently has one item and no active alerts. Destructive removal was not performed. | Pass |
| `/audit` | Symbol/date filters and CSV export | HTTP 200 with one captured decision. Filtering/export controls are present. Browser download could not be freshly exercised. | Pass; export browser verification pending |
| `/checklists` | New/edit, add/remove criterion, save/delete, evaluate symbol | Endpoint returns HTTP 200 and empty admin list. CRUD/evaluate controls are wired; mutation lifecycle was not executed because fresh browser automation was unavailable. | API pass; interaction pending |
| `/account` | Account identity, Google unlink, competence preferences | Account returns ADMIN identity, Google unlinked, local password available. Competence preferences load empty. Google unlink correctly disabled when not linked. | Pass |
| `/admin/jobs` | Refresh, status/enabled filters, select job, run, enable/disable, history/events, scope inputs | Monitor returns seven jobs. External/manual job execution and enable toggles were intentionally not changed. Schedule presentation is misleading while disabled. | Partial; RD-QA-004 |
| `/admin/users` | Email, temporary password, role, create user | ADMIN-only route/control exists. User creation was not performed because there is no UI/API cleanup and it would leave a persistent account. | Authorization/static pass; creation pending controlled test |
| `/admin/seed` | Legacy/admin seed route | Redirects to `/seed` by design. | Pass |
| `/securities/AAPL` | Overview plus Financials, Ratios, Financial health, Valuation, Dividends, Growth, Insider, Business context tabs; watchlist; review link; custom DCF | Detail endpoint succeeds. Custom DCF returns HTTP 200 and a fair value. Several context fields remain unavailable. | Partial; RD-QA-003 |
| `/securities/AAPL/review` | Watchlist, detail/seed links, sources, valuation, checklist, price windows, portfolio add, custom DCF, weighting sliders and review sections | Review endpoint succeeds with a large payload and calculated core analytics. Several provider/internal fields are absent. | Partial; RD-QA-003 |
| Unknown route | SPA fallback | Current route table redirects unknown protected routes to `/`. | Pass by implementation inspection |

## Verified live API behavior

| Workflow | Result |
|---|---|
| Admin login | Success; token omitted |
| Screener, unfiltered | HTTP 200; 65 total entries, 4 pages |
| Screener, Altman SAFE | HTTP 200; 8 entries, demonstrating that this filter affects results |
| Screener sectors | HTTP 200; populated |
| Screener exchanges | HTTP 200; empty array (defect) |
| Universe templates | HTTP 200; populated |
| Universe default preview | HTTP 200; 0 matches |
| Universe stricter numeric preview | HTTP 200; 0 matches |
| AAPL security detail | HTTP 200 |
| AAPL in-depth review | HTTP 200 |
| AAPL custom DCF | HTTP 200; calculated fair value returned |
| Portfolios | HTTP 200; empty for admin |
| Watchlist | HTTP 200; one item |
| Active watchlist alerts | HTTP 200; empty |
| Decision history | HTTP 200; one record |
| Checklists | HTTP 200; empty for admin |
| Account | HTTP 200; ADMIN identity, no Google link |
| Competence preferences | HTTP 200; empty defaults |
| Conservative comparison | HTTP 200; 10 rows |
| Admin jobs monitor | HTTP 200; 7 disabled/skipped jobs |

## Consolidated non-working/missing-feature checklist

- [ ] **High:** Restore exchange metadata so Universe Curation can return candidates.
- [ ] **High:** Add an E2E regression proving market-cap, volume, and max-symbol changes alter a non-empty universe preview.
- [ ] **Medium:** Populate Screener exchange filter/options and exchange cells.
- [ ] **Medium:** Complete Piotroski, Altman, moat, and shares-trend computation for all currently seeded symbols, not only the core real-demo pack.
- [ ] **Medium:** Implement persistent universe symbol exclusions and backend contract.
- [ ] **Medium:** Improve provider/internal coverage for market cap, WACC inputs, DDM, analyst estimates, and capital-allocation fields.
- [ ] **Low:** Clarify disabled job next-run timestamps and do not present `disabled` as an operational error.
- [ ] **Test gap:** Re-run a fresh admin Playwright pass when a browser runtime is available, including CSV download, checklist CRUD/evaluation, portfolio create/holding/simulation/rebalance, watchlist edit/remove/acknowledge, admin user conflict/success validation, and jobs history/events.
- [ ] **Environment-only:** Configure OAuth if Google sign-in must be part of real-demo acceptance.

## Acceptance recommendation

Do not accept Universe Curation as working in the current real-demo build. Other core research pages can be demonstrated, but the demo should either repair exchange data and recompute analytics before presentation or clearly narrow the supported universe. After those fixes, run the pending browser-level controls listed above in an isolated disposable database.
