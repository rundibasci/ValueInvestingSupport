# Requirements — FD1: Interactive Feature Demo Page

## Scope

Implement a single self-contained HTML page served as a Spring Boot static resource that provides a browser-based interactive demo of every feature built through Val2. Replaces the curl-only stakeholder workflow with a clickable interface requiring no tooling beyond a web browser. **No new backend endpoints** — every call targets an existing API.

## Roadmap Reference

Group FD — Phase FD1 (follows Val2; precedes Score1)

---

## Features Exposed

### 1. Authentication Panel
- Username + password inputs + **Login** button → `POST /auth/login` → stores access token in JS memory
- Decodes JWT payload to extract role and expiry; displays "Logged in as {username} ({role})" chip in header
- **Logout** button → `POST /auth/logout` → clears stored token
- ADMIN-only sections hidden (with overlay) until logged in as ADMIN
- Authenticated-but-not-ADMIN sections show "Login as ADMIN to access this section"

### 2. Health Panel *(public — no auth required)*
- **Check Health** button → `GET /actuator/health`
- Renders: overall status chip (green = UP, red = DOWN) + one row per sub-component (`db`, `redis`, `ingestionJobs`, `diskSpace`) with individual status chips
- Accessible without a token so it can be used to verify the backend is running before logging in

### 3. Quick Analysis Panel *(any authenticated role)*
- Ticker symbol input + **Analyze** button → `GET /api/v1/securities/{symbol}/quick-analysis`
- Displays: company name, current price, composite fair value, MoS badge (green ≥ 15 %, yellow 5–15 %, red < 5 % or negative), recommendation label, `dataAsOf` date, `source`
- On 404: "Symbol not found in database — run Seed first."
- On 422: extracts `message` from error body; "Stale data: {message}"
- MiFID II disclaimer displayed below result

### 4. DCF Custom Valuation Panel *(any authenticated role)*
- Inputs: Symbol, WACC (%), Growth Y1–Y5 (%), Growth Y6–Y10 (%), Terminal Rate (%)
- Defaults pre-filled from platform conservative defaults (WACC 9 %, Y1–5 8 %, Y6–10 4 %, terminal 2.5 %)
- **Run DCF** button → `POST /api/v1/securities/{symbol}/valuation/dcf`
- Displays: fair value (base / low / high), enterprise value, parameter snapshot echoed back
- MiFID II disclaimer displayed below result

### 5. Seed Panel *(ADMIN only)*
- Comma-separated ticker input pre-filled with `AAPL,MSFT,KO,JNJ`
- **Seed** button → `POST /api/v1/admin/seed?tickers={input}`
- Results rendered as a table: Symbol | Company | Composite Fair Value | MoS % badge | Recommendation
- Rows with `error` field: show error text in place of badge

### 6. Cache Eviction Panel *(ADMIN only)*
- Symbol input + **Evict** button → `DELETE /api/v1/admin/cache/{symbol}`
- On 204: "Cache evicted for {symbol}"

### 7. Job Trigger Panel *(ADMIN only)*
- Dropdown listing the 7 defined ingestion jobs:
  `bulk-profile-sync`, `bulk-fundamentals-sync`, `bulk-ratios-sync`, `bulk-dcf-sync`,
  `quote-refresh`, `dividend-update`, `insider-trading`
- **Trigger** button → `POST /api/v1/admin/jobs/{jobName}/run`
- On 202: "Job {jobName} triggered (202 Accepted)"

---

## Technical Constraints

| Constraint | Detail |
|---|---|
| Delivery | Single `feature-demo.html` at `backend/src/main/resources/static/feature-demo.html` |
| Stack | Pure HTML5 + vanilla JavaScript + fetch API — no npm, no build step, no bundler, no CDN dependencies |
| CSS | Inline `<style>` block only; no external stylesheets required for offline use |
| Token storage | JS `let` variable — tab-scoped; never localStorage, never cookie |
| Origin | Same-origin as API (served by Spring Boot) — no CORS config needed |
| Auth header | All API calls include `Authorization: Bearer {token}` when a token is in memory |
| Target screen | 1 280 px + desktop; no mobile optimisation required |
| Real-time | No WebSocket, no SSE, no polling |

## Raw JSON Inspector

Every panel has a collapsible `<details><summary>Raw JSON</summary><pre>…</pre></details>` block populated with the full API response after each call — useful for developer inspection without opening DevTools.

## Error Handling

| Condition | Display |
|---|---|
| HTTP 401 from any API call | "Session expired — please log in again"; clear stored token |
| HTTP 403 | "Access denied (role insufficient)" |
| HTTP 404 / 422 / 503 | Extract `message` from response body if present; otherwise HTTP status text |
| Network / fetch throws | "Network error — is the backend running on {BASE_URL}?" |

## Configuration

`const BASE_URL = 'http://localhost:8080'` constant at the top of the `<script>` block — easy to change for any deployment target without a build step.

## Out of Scope

- Screener UI (Group D)
- Portfolio / Watchlist UI (Group F)
- Charts / Recharts visualisations (Group H)
- Mobile / responsive layout
- Token refresh logic (user re-logs when token expires)
- New backend endpoints
- Score / ranking display (Group Score)
