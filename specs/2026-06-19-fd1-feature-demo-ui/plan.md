# Implementation Plan — FD1: Interactive Feature Demo Page

## Approach

Single `feature-demo.html` file. HTML structure → inline CSS → vanilla JS logic at bottom of `<body>`. Spring Boot's default static resource handler serves it at `/feature-demo.html` with no configuration change required.

---

## File to Create

```
backend/src/main/resources/static/feature-demo.html
```

No other files are created or modified.

---

## HTML Layout

```
<body>
  <header>                <!-- title + auth status chip + logout button -->
  <main>
    <div class="top-row"> <!-- two-column: auth-panel | health-panel -->
      <section id="auth-panel">
      <section id="health-panel">
    </div>
    <div class="mid-row">  <!-- two-column: analysis-panel | dcf-panel -->
      <section id="analysis-panel">
      <section id="dcf-panel">
    </div>
    <section id="admin-section">  <!-- full-width; hidden overlay when not ADMIN -->
      <div class="admin-row">     <!-- three-column: seed | cache | jobs -->
        <section id="seed-panel">
        <section id="cache-panel">
        <section id="jobs-panel">
      </div>
    </section>
  </main>
  <footer>                <!-- MiFID II disclaimer -->
</body>
```

---

## CSS Strategy (inline `<style>`)

```css
/* Custom properties */
--green: #22c55e; --yellow: #eab308; --red: #ef4444;
--bg: #0f172a; --surface: #1e293b; --border: #334155;
--text: #e2e8f0; --muted: #94a3b8;

/* Layout */
.top-row, .mid-row { display: grid; grid-template-columns: 1fr 1fr; gap: 1rem; }
.admin-row         { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 1rem; }

/* Card */
section { background: var(--surface); border: 1px solid var(--border);
          border-radius: .5rem; padding: 1.25rem; }

/* Badges */
.badge          { padding: .2rem .6rem; border-radius: .25rem; font-weight: 600; font-size: .85rem; }
.badge-green    { background: var(--green); color: #fff; }
.badge-yellow   { background: var(--yellow); color: #000; }
.badge-red      { background: var(--red);   color: #fff; }
.badge-neutral  { background: var(--border); color: var(--text); }

/* Utility */
.hidden  { display: none !important; }
.overlay { position: relative; }
.overlay::after { content: attr(data-msg); position: absolute; inset: 0;
                  display: flex; align-items: center; justify-content: center;
                  background: rgba(0,0,0,.6); color: var(--muted); font-size: .9rem; }

/* Table */
table { width: 100%; border-collapse: collapse; font-size: .875rem; }
th, td { padding: .4rem .6rem; border-bottom: 1px solid var(--border); text-align: left; }

/* Form elements */
input, select { background: #0f172a; border: 1px solid var(--border);
                color: var(--text); padding: .4rem .6rem; border-radius: .25rem; width: 100%; }
button { background: #3b82f6; color: #fff; border: none; padding: .5rem 1rem;
         border-radius: .25rem; cursor: pointer; margin-top: .5rem; }
button:hover { background: #2563eb; }
```

---

## JavaScript Structure

All logic in one `<script>` block at end of `<body>`.

```javascript
const BASE_URL = 'http://localhost:8080';
let authToken = null;
let userRole  = null;

// ── Auth helpers ──────────────────────────────────────────────
async function login(username, password) { ... }
async function logout() { ... }
function authHeaders() {
  return authToken ? { Authorization: `Bearer ${authToken}` } : {};
}
function decodeJwtPayload(token) {
  return JSON.parse(atob(token.split('.')[1].replace(/-/g,'+').replace(/_/g,'/')));
}
function onLoginSuccess(data) {
  authToken = data.access_token;
  const payload = decodeJwtPayload(authToken);
  userRole = payload.role || payload.roles?.[0] || 'UNKNOWN';
  renderAuthStatus(payload.sub, userRole);
  updateAdminSections(userRole === 'ADMIN');
  updateAuthRequiredSections(true);
}
function onLogout() {
  authToken = null; userRole = null;
  renderAuthStatus(null, null);
  updateAdminSections(false);
  updateAuthRequiredSections(false);
}

// ── API call wrappers ─────────────────────────────────────────
async function apiCall(method, path, body = null) {
  const opts = { method, headers: { 'Content-Type': 'application/json', ...authHeaders() } };
  if (body) opts.body = JSON.stringify(body);
  const res = await fetch(BASE_URL + path, opts);
  if (res.status === 401) { onLogout(); throw new Error('SESSION_EXPIRED'); }
  return res;  // caller handles status
}

async function callSeed(tickers)              { ... } // POST /api/v1/admin/seed?tickers=
async function callQuickAnalysis(symbol)      { ... } // GET  /api/v1/securities/{symbol}/quick-analysis
async function callDcf(symbol, params)        { ... } // POST /api/v1/securities/{symbol}/valuation/dcf
async function evictCache(symbol)             { ... } // DELETE /api/v1/admin/cache/{symbol}
async function triggerJob(jobName)            { ... } // POST /api/v1/admin/jobs/{jobName}/run
async function checkHealth()                  { ... } // GET  /actuator/health

// ── Render helpers ────────────────────────────────────────────
function mosBadge(mos) {
  if (mos == null) return '<span class="badge badge-neutral">N/A</span>';
  if (mos >= 15)   return `<span class="badge badge-green">${mos.toFixed(1)} %</span>`;
  if (mos >= 5)    return `<span class="badge badge-yellow">${mos.toFixed(1)} %</span>`;
  return             `<span class="badge badge-red">${mos.toFixed(1)} %</span>`;
}
function statusBadge(status) {
  return status === 'UP'
    ? '<span class="badge badge-green">UP</span>'
    : '<span class="badge badge-red">' + (status || 'DOWN') + '</span>';
}
function showRaw(panelId, data) {
  document.querySelector(`#${panelId} pre`).textContent = JSON.stringify(data, null, 2);
}
function showError(panelId, msg) {
  document.querySelector(`#${panelId} .result`).innerHTML =
    `<span style="color:var(--red)">${msg}</span>`;
}

// ── Visibility toggles ────────────────────────────────────────
function updateAdminSections(isAdmin) {
  const msg = isAdmin ? '' : 'Login as ADMIN to access this section';
  document.querySelectorAll('.admin-only').forEach(el => {
    el.dataset.msg = msg;
    el.classList.toggle('overlay', !isAdmin);
  });
}
function updateAuthRequiredSections(isAuthed) {
  const msg = isAuthed ? '' : 'Login to access this section';
  document.querySelectorAll('.auth-required').forEach(el => {
    el.dataset.msg = msg;
    el.classList.toggle('overlay', !isAuthed);
  });
}

// ── Event listeners ───────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
  // Auth
  document.getElementById('login-btn').addEventListener('click', ...);
  document.getElementById('logout-btn').addEventListener('click', ...);
  // Health
  document.getElementById('health-btn').addEventListener('click', ...);
  // Quick Analysis
  document.getElementById('analyze-btn').addEventListener('click', ...);
  // DCF
  document.getElementById('dcf-btn').addEventListener('click', ...);
  // Seed
  document.getElementById('seed-btn').addEventListener('click', ...);
  // Cache eviction
  document.getElementById('evict-btn').addEventListener('click', ...);
  // Job trigger
  document.getElementById('job-btn').addEventListener('click', ...);

  // Initial state — not logged in
  updateAdminSections(false);
  updateAuthRequiredSections(false);
});
```

---

## Panel HTML Snippets (representative)

### Quick Analysis
```html
<section id="analysis-panel" class="auth-required">
  <h3>Quick Analysis</h3>
  <input id="analysis-symbol" type="text" placeholder="e.g. AAPL">
  <button id="analyze-btn">Analyze</button>
  <div class="result"></div>
  <p class="disclaimer">This is a decision-support tool, not investment advice (MiFID II).</p>
  <details><summary>Raw JSON</summary><pre></pre></details>
</section>
```

### DCF Custom Valuation
```html
<section id="dcf-panel" class="auth-required">
  <h3>DCF Custom Valuation</h3>
  <input id="dcf-symbol"   placeholder="Symbol">
  <input id="dcf-wacc"     type="number" value="9.0"  step="0.1" placeholder="WACC %">
  <input id="dcf-g15"      type="number" value="8.0"  step="0.1" placeholder="Growth Y1–5 %">
  <input id="dcf-g610"     type="number" value="4.0"  step="0.1" placeholder="Growth Y6–10 %">
  <input id="dcf-terminal" type="number" value="2.5"  step="0.1" placeholder="Terminal Rate %">
  <button id="dcf-btn">Run DCF</button>
  <div class="result"></div>
  <p class="disclaimer">This is a decision-support tool, not investment advice (MiFID II).</p>
  <details><summary>Raw JSON</summary><pre></pre></details>
</section>
```

### Health
```html
<section id="health-panel">
  <h3>System Health</h3>
  <button id="health-btn">Check Health</button>
  <div class="result"></div>
  <details><summary>Raw JSON</summary><pre></pre></details>
</section>
```

---

## No Backend Changes Required

All endpoints already exist:
- `POST /auth/login` (A3)
- `POST /auth/logout` (A3)
- `GET /actuator/health` (A1)
- `GET /api/v1/securities/{symbol}/quick-analysis` (Val1)
- `POST /api/v1/securities/{symbol}/valuation/dcf` (C3)
- `POST /api/v1/admin/seed` (Val2)
- `DELETE /api/v1/admin/cache/{symbol}` (B2)
- `POST /api/v1/admin/jobs/{jobName}/run` (B3)

Spring Boot serves `src/main/resources/static/` automatically — no `WebMvcConfigurer` change needed.

---

## Recommended Demo Flow (for stakeholder walkthrough)

1. Open `http://localhost:8080/feature-demo.html`
2. **Health Check** — verify all components are UP
3. **Login** as `admin@example.com / Admin1234!`
4. **Seed** `AAPL,KO` — see fair values and MoS badges appear
5. **Quick Analysis** for `AAPL` — see full recommendation card
6. **DCF** for `AAPL` with custom WACC — see scenario range
7. **Evict Cache** for `AAPL` — confirm eviction
8. **Trigger** `quote-refresh` job — confirm 202
9. **Logout**
