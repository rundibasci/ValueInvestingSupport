# Validation Criteria — FD1: Interactive Feature Demo Page

## Delivery Checklist

- [ ] `backend/src/main/resources/static/feature-demo.html` created
- [ ] No other backend files added or modified
- [ ] Page loads at `http://localhost:8080/feature-demo.html` with no 404 or console errors
- [ ] No external CDN links, no npm, no build step required to use the page

---

## Manual Browser Tests

Start the backend with the `local` profile (PostgreSQL + Redis via Docker Compose, FMP key in `.env`).

### Auth Panel

| # | Action | Expected |
|---|---|---|
| A1 | Load the page without logging in | Login form visible; Quick Analysis and DCF show "Login required" overlay; Admin section shows "Login as ADMIN" overlay; Health panel has Check Health button (no overlay) |
| A2 | Enter `admin@example.com` / `Admin1234!`, click Login | Header shows "Logged in as admin@example.com (ADMIN)"; ADMIN panels become interactive; Quick Analysis and DCF panels become interactive |
| A3 | Click Logout | Returns to login form; all overlays restored; no token in memory |
| A4 | Enter bad credentials | Inline error "Login failed — check credentials" (HTTP 401 handled) |

### Health Panel

| # | Action | Expected |
|---|---|---|
| H1 | Click "Check Health" (not logged in) | Status chip shows overall UP/DOWN; rows for db, redis, ingestionJobs, diskSpace with individual chips |
| H2 | Click "Check Health" with backend stopped | "Network error — is the backend running on http://localhost:8080?" |

### Quick Analysis Panel

| # | Action | Expected |
|---|---|---|
| Q1 | Login, enter `AAPL` (after Seed run), click Analyze | Analysis card: company name, current price, composite fair value, MoS badge (colour-coded), recommendation, `dataAsOf`, source="fmp". MiFID II disclaimer visible. |
| Q2 | Enter an unrecognised ticker (e.g. `ZZZZZ`) | "Symbol not found in database — run Seed first." |
| Q3 | Enter a seeded ticker with a snapshot older than 7 days | "Stale data: {message from 422 body}" |
| Q4 | Open Raw JSON `<details>` | Full JSON response visible in `<pre>` block |

### DCF Custom Valuation Panel

| # | Action | Expected |
|---|---|---|
| D1 | Login, enter `AAPL` + default params, click Run DCF | Fair value base / low / high displayed, enterprise value shown, parameter snapshot echoed. MiFID II disclaimer visible. |
| D2 | Clear symbol, click Run DCF | Inline validation message "Symbol is required" (client-side guard, no API call made) |
| D3 | Open Raw JSON `<details>` | Full JSON response visible |

### Seed Panel (ADMIN)

| # | Action | Expected |
|---|---|---|
| S1 | Login as ADMIN; enter `AAPL,KO`; click Seed | Results table with 2 rows; MoS badges are colour-coded; company names shown |
| S2 | Include an invalid ticker `AAPL,ZZZZZ,KO` | 3 rows: AAPL ✓, ZZZZZ shows error text in place of badge, KO ✓ |
| S3 | Open Raw JSON `<details>` | Full response array visible |

### Cache Eviction Panel (ADMIN)

| # | Action | Expected |
|---|---|---|
| C1 | Login as ADMIN; enter `AAPL`; click Evict | "Cache evicted for AAPL" confirmation message |
| C2 | Enter blank symbol | Client-side guard: "Symbol is required" |

### Job Trigger Panel (ADMIN)

| # | Action | Expected |
|---|---|---|
| J1 | Login as ADMIN; select `quote-refresh`; click Trigger | "Job quote-refresh triggered (202 Accepted)" |
| J2 | Select each of the 7 jobs from the dropdown | All 7 appear in the dropdown; none missing |

### Error & Session Handling

| # | Condition | Expected |
|---|---|---|
| E1 | Manually clear `authToken` in browser console; attempt Quick Analysis | "Session expired — please log in again"; page returns to login state |
| E2 | Non-ADMIN role token (if creatable via admin user provisioning) | ADMIN panels show overlay; Quick Analysis and DCF work normally |

---

## Scope Confirmation

The following are **not** tested here (covered by future groups):

- Score / ranking display (Score group)
- Screener filters (Group D)
- Portfolio or watchlist management (Group F)
- Charts or time-series visualisations (Group H)
- Mobile responsive layout
- Token refresh / auto-renewal
