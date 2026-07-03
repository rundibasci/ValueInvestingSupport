# Roadmap — Value Investing Advisory Platform

Implementation broken into small, shippable phases. Each phase produces working, tested code that the next phase builds upon. Phases within a group can overlap but should be completed in listed order within the group.

**Data source strategy:**
- **Demo milestone (M0):** Yahoo Finance public API — zero cost, no API key, sufficient for a single-stock vertical slice.
- **Production milestone (M1+):** FMP Premium — official, bulk, full screener. Switch is isolated to the data client layer; Valuation Engine and Score Engine are untouched.
- **Yahoo Finance fallback (all milestones):** Yahoo Finance remains a live fallback in every phase after M0. If FMP is unavailable (quota exceeded, outage, key missing), `MarketDataClient` switches to `YahooMarketDataClient` automatically — same domain types, no code changes outside the client layer. Control via `MARKET_DATA_SOURCE=fmp` (default for M1+) or `MARKET_DATA_SOURCE=yahoo`.

**FMP API key — local setup (required from B1 onward):**
- Copy `.env.example` to `.env` (gitignored) and set `FMP_API_KEY=<your-key>` and `MARKET_DATA_SOURCE=fmp`.
- For integration tests that hit FMP directly: create `backend/src/test/resources/application-fmpkey.yml` (gitignored via `**/application-fmpkey.yml`) containing `fmp.api-key: <your-key>`, then annotate the test class with `@ActiveProfiles({"test","fmpkey"})`.
- Neither file is ever committed. See `specs/tech-stack.md` → *Secrets & Local Configuration* for the full pattern.

---

## Group Z — Demo (Vertical Slice, Zero Cost)

Goal: demonstrate the core value proposition — *"give me a ticker, I'll tell you if it's undervalued"* — end to end, with no paid API, no auth, no database persistence. Single REST call + simple UI page.

### Phase Z1: Backend Scaffold (Minimal)
- Init Spring Boot 3.x project (Maven, Java 21)
- `docker-compose.yml` with PostgreSQL + Redis (needed from Group A onward; optional for pure demo)
- `application.yml` with profile `demo` (no auth, no DB required, in-memory cache only)
- Spring Boot Actuator health endpoint
- `.env.example`

### Phase Z2: Yahoo Finance Client
- `YahooFinanceClient` using Spring WebClient (no API key)
- Endpoints used:
  - `quoteSummary` with modules: `financialData`, `defaultKeyStatistics`, `incomeStatementHistory`, `balanceSheetHistory`, `cashflowStatementHistory`, `summaryDetail`, `assetProfile`
  - `chart/{symbol}` for current price
- Response DTOs (Java records) mapping Yahoo Finance JSON structure
- In-memory cache (`Caffeine`) with 15-min TTL — no Redis required for demo
- Adapter: maps Yahoo Finance DTOs → domain `FundamentalSnapshot` + `RatioSnapshot` records

### Phase Z3: Valuation Engine (Demo-compatible)
- `GrahamCalculator.calculate(eps, bvps) → BigDecimal`
- `DcfCalculator.calculate(DcfInput) → DcfResult` (pessimistic / base / optimistic scenarios)
- `MarginOfSafetyCalculator.compute(fairValue, currentPrice) → BigDecimal`
- RULE-06 guard: DCF skipped (returns null) if < 3 years of positive FCF found in Yahoo data
- Unit tests with hardcoded reference values (AAPL or similar known stock)

### Phase Z4: Demo Analysis Endpoint
- `GET /demo/analyze/{symbol}` — no auth required
- Calls Yahoo Finance client → maps to domain types → runs valuation → returns JSON
- Response includes:
  ```json
  {
    "symbol": "AAPL",
    "companyName": "Apple Inc.",
    "currentPrice": 182.5,
    "currency": "USD",
    "sector": "Technology",
    "financialSummary": { "revenue": ..., "netIncome": ..., "fcf": ..., "eps": ... },
    "valuation": {
      "dcf": { "fairValue": 210.5, "low": 185.0, "high": 230.0 },
      "grahamNumber": 9.51,
      "composite": 155.0
    },
    "marginOfSafety": 13.6,
    "recommendation": "QUALITY_VALUE",
    "disclaimer": "This is a decision-support tool, not investment advice (MiFID II)."
  }
  ```
- Error cases: symbol not found (404), Yahoo Finance unavailable (503 with message)

### Phase Z5: Demo UI (Single Page)
- Minimal React page (can be a single `index.html` with CDN React for speed, or Vite project)
- Ticker input field + "Analyze" button
- Displays: company name, current price, DCF fair value, Graham Number, composite fair value, MoS badge (color-coded), recommendation label
- MoS gauge: green (> 15%), yellow (5–15%), red (< 5% or negative)
- Disclaimer footer (MiFID II)
- No routing, no auth, no state management library needed

---

## Group A — Foundation

### Phase A1: Backend Project Scaffold
- Init Spring Boot 3.x project (Maven, Java 21)
- `docker-compose.yml` for PostgreSQL + Redis (local dev)
- `application.yml` with profiles: `local`, `test`, `prod`
- Spring Boot Actuator health endpoint
- Flyway configured, first empty migration `V1__init.sql`
- `.env.example` with all required environment variables

### Phase A2: Domain Entities & DB Schema
- JPA entities: `Security`, `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote`
- JPA entities: `ValuationResult`, `ValueScore`, `DividendRecord`, `InsiderTrade`
- JPA entities: `User`, `Portfolio`, `Holding`, `Watchlist`, `Alert`
- Flyway migration `V2__core_schema.sql` with all tables + indexes
- Partitioned `price_quote` table by month
- Spring Data repositories for each entity

### Phase A3: Authentication
- `POST /auth/login` → JWT (RS256) access + refresh tokens
- `POST /auth/refresh` → new access token
- `POST /auth/logout` → revoke refresh token
- Spring Security filter chain (permit `/auth/**`, require auth on `/api/**`)
- `User` registration (admin-only for MVP)

---

## Group B — Data Pipeline

### Phase B1: Market Data Client Abstraction
- `MarketDataClient` interface: `getProfile(symbol)`, `getFundamentals(symbol)`, `getRatios(symbol)`, `getQuote(symbol)`
- Two implementations behind the same interface:
  - `YahooMarketDataClient` — reuses Z2 Yahoo Finance client; active when `MARKET_DATA_SOURCE=yahoo`; **also serves as runtime fallback** when FMP is unavailable (quota exceeded, HTTP 429/503 after retries, key absent)
  - `FmpMarketDataClient` — FMP WebClient with API key, retry (exponential backoff, max 3), error handling (429/503); active when `MARKET_DATA_SOURCE=fmp`; on non-retryable failure delegates to `YahooMarketDataClient`
- Spring `@Profile` or `@ConditionalOnProperty` to select primary implementation at startup; fallback wiring is always present regardless of active source
- DTOs and adapters isolated per client; domain entities are shared

### Phase B2: Redis Cache Layer
- `@Cacheable` wrappers on all market data client calls
- TTL configuration per data type (15 min quotes, 6h quarterly, 24h annual/profile)
- Cache key strategy: `mdc:{source}:{endpoint}:{symbol}:{params_hash}`
- Cache eviction on manual refresh

### Phase B3: Data Ingestion Jobs
- Nightly 02:00 — Bulk Profile Sync (exchange by exchange)
- Nightly 03:00 — Bulk Fundamentals Sync (income, balance, cash flow)
- Nightly 03:30 — Bulk Ratios Sync (ratios TTM bulk)
- Nightly 04:00 — Bulk DCF Sync
- Every 15 min — Quote Refresh (watchlist + holdings symbols only)
- Nightly 06:00 — Dividend Update (watchlist + holdings)
- Hourly — Insider Trading feed
- Job run log table + Spring Actuator job health indicator

---

## Group LS — Local Stack Demo

Goal: validate the auth + cache stack is working end-to-end before valuation work begins. Runs entirely with H2 in-memory database and Docker Redis — no PostgreSQL container needed. Provides a clickable HTML page to manually test login, JWT handling, and protected endpoint access with the built-in admin user. Requires B1 and B2 only; B3 can follow after.

### Phase LS1: H2 Demo Profile & Admin Seed
- `application-demo.yml` profile: H2 in-memory datasource, Redis at `localhost:6379` (Docker)
- Flyway H2-compatible dialect — DDL reviewed for H2 compatibility; H2-incompatible syntax guarded per migration file
- `DemoDataSeeder` `@Component` (active on `demo` profile only): on startup inserts `User { username=admin, password=BCrypt("admin"), role=ADMIN }` if not already present
- `docker-compose.demo.yml`: Redis only (no PostgreSQL — H2 replaces it)
- Smoke test: `GET /actuator/health` → returns `UP` with H2 + Redis status indicators

### Phase LS2: HTML Demo Client
- Single `demo.html` served as Spring Boot static resource (`src/main/resources/static/demo.html`)
- Login form → `POST /auth/login` (username: `admin` / password: `admin`) → JWT stored in JS memory
- "Ping Admin" button → `GET /api/v1/admin/ping` with `Authorization: Bearer <token>` header; new admin-only endpoint returning `{ "status": "ok", "role": "ADMIN" }`
- Response panel shows: HTTP status, decoded role from JWT, cache hit/miss response header
- Pure HTML + vanilla JS + fetch API — no build step, no framework, no bundler

---

## Group C — Valuation Engine

### Phase C1: Graham Number & DDM
- `GrahamCalculator.calculate(eps, bvps) → BigDecimal`
- `DdmCalculator.calculate(dpsTtm, dividendGrowthRate, requiredReturn) → BigDecimal`
- RULE-07 guard: DDM throws if < 5 consecutive dividend years
- Unit tests for both with known reference values

### Phase C2: DCF Engine
- `DcfCalculator.calculate(DcfInput) → DcfResult`
- `DcfInput`: fcfTtm, growthY1Y5, growthY6Y10, terminalRate, wacc, shares, netDebt
- `DcfResult`: fairValue, fairValueLow (WACC+2%), fairValueHigh (WACC-1%), enterpriseValue, parameters snapshot
- RULE-06 guard: DCF throws if < 3 years of positive FCF
- Unit tests with reference calculation (AAPL-like inputs → verify formula)

### Phase C3: Composite Fair Value & Margin of Safety
- `ValuationService.calculate(symbol, params) → ValuationResult`
- Composite: DCF 60%, Graham 25%, DDM 15% (weights configurable, DDM only if eligible)
- `MarginOfSafetyCalculator.compute(fairValue, currentPrice) → BigDecimal`
- Persist `ValuationResult` to DB
- `POST /api/v1/securities/{symbol}/valuation/dcf` endpoint

---

## Group Val — Connected Valuation Demo

Goal: validate and demonstrate the full production value chain with real FMP data, auth, and DB-backed state — before building the screener. Produces an authenticated, stakeholder-showable endpoint that delivers the core promise: *"give me a ticker, I'll tell you if it's undervalued."* The M0 demo proved the concept; Val proves the production system.

### Phase Val1: Single-Stock Analysis Endpoint
- `GET /api/v1/securities/{symbol}/quick-analysis` (auth required, all roles)
- Reads latest `FundamentalSnapshot` + `RatioSnapshot` from DB (populated by B3 ingestion)
- Fetches current price from Redis cache; falls back to most recent `PriceQuote` in DB
- Calls `ValuationService.calculate()` → composite fair value, MoS, recommendation
- Response structure mirrors the M0 schema; adds `dataAsOf` (snapshot date) and `"source": "fmp"` (or `"yahoo"` when FMP fallback was used)
- Error cases: 404 if ticker not in DB, 422 if snapshot older than 7 days (stale data guard)
- RULE-06 guard respected: DCF omitted if < 3 years of positive FCF; falls back to Graham-only composite
- MiFID II disclaimer required in response body
- Unit test: `QuickAnalysisControllerTest` (MockMvc, mocked service)

### Phase Val2: Demo Seed Endpoint & Smoke Test
- `POST /api/v1/admin/seed` (ADMIN only) — triggers on-demand FMP ingestion for a configurable ticker list (`SEED_TICKERS` env var, default `AAPL,MSFT,KO,JNJ`)
- Seeded securities are platform-wide reference data: after seeding, every authenticated user can discover the same symbols through search, screener, security detail, watchlist, and portfolio flows, while personal watchlists/portfolios remain user-owned
- Add named seed packs for common research universes (default value pack, US large cap, dividend candidates) so an admin can seed a useful shared universe without manually typing every ticker
- For each ticker: fetch company profile → fundamentals → ratios → price quote → run `ValuationService` → persist
- Response: array of `{ symbol, companyName, compositeFairValue, marginOfSafety, recommendation, source, error }` per seeded ticker
- Integration test `ValuationDemoIT`: login as admin → `POST /api/v1/admin/seed?tickers=AAPL` → `GET /api/v1/securities/AAPL/quick-analysis` → assert `marginOfSafety` non-null and `recommendation` set
- `scripts/demo.sh`: documented curl sequence (login → seed → analyze → logout) — shareable with stakeholders

---

## Group FD — Interactive Feature Demo

Goal: replace the curl-only stakeholder workflow with a browser-based clickable interface that exposes every feature built through Val2 — auth, health, seed, quick analysis, DCF custom valuation, cache eviction, and job trigger — in a single self-contained HTML page. No new backend endpoints; no build step required. Makes M3.5 immediately shareable with non-technical stakeholders.

### Phase FD1: Feature Demo Page
- `feature-demo.html` served as Spring Boot static resource (`src/main/resources/static/feature-demo.html`)
- **Auth panel:** username + password → `POST /auth/login` → JWT stored in JS memory; Logout → `POST /auth/logout`; decoded role shown in header; ADMIN-only and auth-required sections toggled accordingly
- **Health panel** *(public)*: **Check Health** → `GET /actuator/health` → overall status chip + per-component rows (`db`, `redis`, `ingestionJobs`, `diskSpace`)
- **Quick Analysis panel** *(any authenticated role)*: symbol input → `GET /api/v1/securities/{symbol}/quick-analysis` → company name, price, composite fair value, MoS badge (green ≥ 15 %, yellow 5–15 %, red < 5 %), recommendation, `dataAsOf`; MiFID II disclaimer
- **DCF Custom Valuation panel** *(any authenticated role)*: symbol + WACC + growth Y1–5 + growth Y6–10 + terminal rate → `POST /api/v1/securities/{symbol}/valuation/dcf` → fair value base/low/high, enterprise value, parameter echo; MiFID II disclaimer
- **Seed panel** *(ADMIN only)*: ticker CSV input → `POST /api/v1/admin/seed` → table of results with MoS badges; per-ticker error rows shown inline
- **Cache Eviction panel** *(ADMIN only)*: symbol → `DELETE /api/v1/admin/cache/{symbol}` → confirmation message
- **Job Trigger panel** *(ADMIN only)*: dropdown of 7 job names → `POST /api/v1/admin/jobs/{jobName}/run` → 202 confirmation
- Every panel has a collapsible raw-JSON inspector (`<details>`) for developer use
- Pure HTML5 + vanilla JavaScript + fetch API — no npm, no bundler, no CDN dependencies; runs offline against any backend URL (`BASE_URL` constant at top of script)

---

## Group Score — Full Pipeline Demo

Goal: validate and demonstrate the complete data → valuation → scoring → ranking chain before building the full screener. An authenticated admin endpoint accepts a ticker list, seeds FMP data for each ticker, runs the valuation engine, computes a composite Value Score, and returns the tickers ranked by score — stakeholder-showable before any screener UI exists.

### Phase Score1: Pipeline Run Endpoint
- `POST /api/v1/admin/pipeline-run` (ADMIN only), body: `{ "tickers": ["AAPL", "MSFT", "KO", "JNJ"] }`
- For each ticker: fetch + persist `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote` (via `MarketDataClient`), run `ValuationService.calculate()` → persist `ValuationResult`
- Compute `ValueScore` using the full 5-factor formula (MoS 30, Quality 25, Safety 20, Growth 15, Dividend 10) — same formula that D1 will expose individually; persist `ValueScore`
- Response: array sorted by `totalScore DESC`:
  ```json
  [
    {
      "symbol": "KO",
      "companyName": "Coca-Cola Co.",
      "compositeFairValue": 58.2,
      "marginOfSafety": 18.4,
      "totalScore": 72.5,
      "recommendation": "QUALITY_VALUE"
    }
  ]
  ```
- Re-uses `MarketDataClient`, `ValuationService`; introduces `ValueScoreService` (same class D1 will use — no duplication at merge time)

### Phase Score2: Integration Test & Demo Script
- Integration test `PipelineDemoIT` (uses `localstack` profile + H2 + Testcontainers Redis or Docker Redis):
  - Login as admin → `POST /api/v1/admin/pipeline-run` with `["AAPL"]` → assert response contains `totalScore` non-null and `marginOfSafety` non-null; assert ticker ranked first in list
- `scripts/pipeline-demo.sh`: documented curl sequence (login → pipeline-run → print ranked table) — shareable with stakeholders

---

## Group D — Scoring & Screener

### Phase D1: Value Score Engine
- `ValueScoreService.compute(symbol) → ValueScore`
- Five sub-scores: MoS (30), Quality (25), Safety (20), Growth (15), Dividend (10)
- Score formula exactly as per spec section 6.5
- Persist `ValueScore` to DB
- `GET /api/v1/securities/{symbol}/score` endpoint

### Phase D2: Stock Screener API
- `POST /api/v1/screener` with `ScreenerRequest` (all filters from spec 6.2)
- Query executes on local DB only (never calls FMP live)
- Pagination: page + pageSize
- Sort: any field, default `valueScore DESC`
- `GET /api/v1/screener/presets` — Graham, Dividend, Quality presets
- `GET /api/v1/screener/sectors` and `/exchanges` from local `security` table
- Results include market-research context for each symbol: company name, sector, exchange, country when available, and a concise description/profile excerpt so users can research across the seeded market before opening a detail page
- Performance target: < 500ms for 5000+ rows with typical filters

---

## Group E — Security Detail API

### Phase E1: Company Profile & Financials
- `GET /api/v1/securities/search?q=` — autocomplete across the shared seeded universe; searches symbol, company name, and description/profile text when available
- Search response includes symbol, company name, sector, exchange, country when available, and description/profile excerpt so users can research all seeded markets without already knowing the ticker
- `GET /api/v1/securities/{symbol}` — full profile (company info + latest snapshot)
- `GET /api/v1/securities/{symbol}/financials` — 10y annual + 8 quarters + TTM
- `GET /api/v1/securities/{symbol}/ratios` — 10y ratio history

### Phase E2: Financial Health, Dividends, Insiders & Growth
- `GET /api/v1/securities/{symbol}/financial-health` — 10y annual + TTM trend view of revenue, net income, FCF, cash, total debt, short-term debt, long-term debt, net debt, current ratio, quick ratio, interest coverage, debt-to-equity, net-debt-to-EBITDA/FCF where meaningful, and dividend coverage
- Return metric definitions, data availability, and sector/industry context with the series; the API must not assign universal leverage thresholds or an investment recommendation
- `GET /api/v1/securities/{symbol}/dividends` — full dividend history + streak + growth CAGR
- Dividend response includes payout ratios based on earnings and FCF, plus dividend coverage where data is available
- `GET /api/v1/securities/{symbol}/insiders` — recent insider transactions
- `GET /api/v1/securities/{symbol}/growth` — CAGR at 3y, 5y, 10y for revenue, FCF, EPS
- `GET /api/v1/securities/{symbol}/peers` — peer comparison table

### Phase E3: Full Valuation on Security Detail
- `GET /api/v1/securities/{symbol}/valuation` — all models (DCF, Graham, DDM, FMP DCF)
- Includes Fair Value composite, MoS, scenario range
- Analyst estimates + price target consensus aggregated
- Security detail research packet must surface the complete stock-analysis checklist in one navigable experience: DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, and data-unavailable labels when provider coverage is incomplete

---

## Group F — Portfolio & Watchlist

### Phase F1: Watchlist
- `GET/POST/PUT/DELETE /api/v1/watchlist` CRUD
- Alert thresholds per watchlist item (mosMin, mosMax, fundamentalDegrade)
- `GET /api/v1/watchlist/alerts` — list active alerts

### Phase F2: Portfolio CRUD & Holdings
- `GET/POST /api/v1/portfolios` — create / list portfolios
- `GET /api/v1/portfolios/{id}` — detail with current weights and MoS
- `POST/PUT/DELETE /api/v1/portfolios/{id}/holdings` — manage holdings

### Phase F3: Portfolio Builder (Simulation)
- `POST /api/v1/portfolios/{id}/simulate` — propose allocation from watchlist
- Apply constraints: 25% max/stock, 40% max/sector, 50% max/country
- Value-weighted by ValueScore normalized
- Output: proposed weights, shares, cost, weighted yield, average MoS

### Phase F4: Rebalancing
- `GET /api/v1/portfolios/{id}/rebalance` — diff current vs target weights
- Suggest buys/sells to realign, respecting hard rules

---

## Group PFD — Portfolio & Full-Feature HTML Demo

Goal: replace the curl-and-script stakeholder workflow with a single, self-contained HTML page that exercises every endpoint built through Group F — screener, security detail, watchlist, portfolio management, simulation, and rebalancing — in addition to all panels already present in FD1. No new backend endpoints; no build step required. Makes the complete system demonstrable in a browser before the full React UI (Group H) is started.

### Phase PFD1: Complete Feature Demo Page
- `full-demo.html` served as Spring Boot static resource (`src/main/resources/static/full-demo.html`)
- Inherits all panels from FD1 (auth, health, seed, quick analysis, DCF custom valuation, cache eviction, job trigger)
- **Seed panel** *(ADMIN only)*: supports ticker CSV and named shared-universe seed packs; seeded securities become discoverable by all authenticated users
- **Screener panel** *(any authenticated role)*: filter form (sector, exchange, MoS min/max, score min) → `POST /api/v1/screener` → sortable results table with MoS badges and company description/profile excerpt; preset buttons (Graham, Dividend, Quality) → `GET /api/v1/screener/presets`; click row fills Security Detail panel symbol
- **Security Detail panel** *(any authenticated role)*: symbol input → tabbed sub-panels:
  - Profile & Financials: `GET /api/v1/securities/{symbol}` + `GET /api/v1/securities/{symbol}/financials`
  - Ratios: `GET /api/v1/securities/{symbol}/ratios`
  - Full Valuation: `GET /api/v1/securities/{symbol}/valuation`
  - Dividends: `GET /api/v1/securities/{symbol}/dividends`
  - Growth: `GET /api/v1/securities/{symbol}/growth`
  - Insiders: `GET /api/v1/securities/{symbol}/insiders`
  - Peers: `GET /api/v1/securities/{symbol}/peers`
  - Add to Watchlist button inline
- **Watchlist panel** *(any authenticated role)*:
  - List: `GET /api/v1/watchlist` → table with MoS badge + inline alert threshold inputs
  - Add item: `POST /api/v1/watchlist`; update threshold: `PUT /api/v1/watchlist/{id}`; remove: `DELETE /api/v1/watchlist/{id}`
  - Active alerts sub-section: `GET /api/v1/watchlist/alerts` → dismissable rows
- **Portfolio panel** *(any authenticated role)*:
  - Create/list: `GET/POST /api/v1/portfolios` → portfolio selector dropdown
  - Holdings table for selected portfolio: `GET /api/v1/portfolios/{id}` → add/update/delete holdings via inline forms (`POST/PUT/DELETE /api/v1/portfolios/{id}/holdings`)
  - Simulate button: `POST /api/v1/portfolios/{id}/simulate` → proposed allocation table (weights, shares, cost, sector %, average MoS)
  - Rebalance button: `GET /api/v1/portfolios/{id}/rebalance` → buy/sell recommendation table
- Every panel has a collapsible raw-JSON inspector (`<details>`) for developer use
- Pure HTML5 + vanilla JavaScript + fetch API — no npm, no bundler, no CDN dependencies; `BASE_URL` constant at top of script for configurable backend target

---

## Group G — Alert Engine

### Phase G1: Alert Detection Job
- Nightly job: recompute MoS for all watchlist/portfolio symbols after price refresh
- Detect all 8 alert types (spec section 6.7)
- Persist `Alert` records; deduplicate (don't re-alert same condition same day)

### Phase G2: Alert Delivery
- In-app alert endpoint: `GET /api/v1/watchlist/alerts`
- Email notification (SMTP / SendGrid) for HIGH priority alerts
- Alert acknowledgement (`PUT /api/v1/alerts/{id}/ack`)

---

## Group H — Frontend

### Phase H1: Frontend Scaffold
- Vite + React 18 + TypeScript + TailwindCSS project
- React Router v6 routes: `/`, `/screener`, `/securities/:symbol`, `/securities/:symbol/review`, `/portfolio`, `/watchlist`
- TanStack Query client setup + auth token interceptor
- Layout: sidebar nav + main content area + header

### Phase H2: Authentication UI
- Login page → POST `/auth/login`
- Token storage (memory + httpOnly cookie for refresh)
- Protected route wrapper

### Phase H3: Screener UI
- Filter panel (all screener filters with range sliders + dropdowns)
- Market-wide research/search experience across the shared seeded universe, including symbol, company name, sector, exchange, country where available, and description/profile excerpt in results
- Results table: sortable columns, pagination
- Preset selector (Graham / Dividend / Quality)
- Click row → navigate to Security Detail

### Phase H4: Security Detail UI
- Overview tab: company profile, sector, country, market cap, management
- Financials tab: 10y revenue/income/FCF bar charts (Recharts)
- Ratios tab: PE, ROIC, ROE, debt trend line charts, current ratio, quick ratio when available, and payout ratio
- Financial Health tab: synchronized 5–10y charts for debt (total, short-term, long-term and net debt) against revenue, net income and FCF; liquidity and interest-coverage trends; dividend sustainability from earnings/FCF payout and coverage
- Financial Health tab labels metrics and their data availability, presents sector/industry context, and uses trend-oriented caution indicators rather than universal safe/unsafe leverage ratings
- Valuation tab: DCF custom form, Fair Value vs price, DCF base/low/high, Graham number, FCF basis, MoS gauge, recommendation, and analyst target context when available
- Dividends tab: dividend history bar chart, streak, payout ratio, dividend yield, and dividend sustainability/coverage
- Growth tab: CAGR table at 3/5/10y
- Insider tab: recent trades table
- Add to Watchlist button
- Prominent link/button to the separate In-Depth Review page for the same symbol.

### Phase H4A: In-Depth Stock Review Page
- Dedicated route: `/securities/:symbol/review`.
- Dedicated page/component: `SecurityReviewPage`, separate from `SecurityDetailPage`.
- Accessible to every authenticated role for any symbol in the shared seeded universe.
- Entry points from Screener rows, Security Detail header/actions, Watchlist rows, Portfolio holding rows, and Seed result rows.
- Presents a single focused research packet for one stock with a compact table-of-contents, jump links, and sticky scroll progress, optimized for reading and comparison rather than tab navigation.
- Header shows company name, ticker, sector, exchange, country, currency, current price, price date, provider badges, freshness/staleness, and data-source limitations.
- Data source section shows `FMP`, `Yahoo Finance`, or `Mixed` coverage by category: profile, fundamentals, ratios, quote, dividends, valuation, score, and analyst estimates.
- Valuation section shows DCF base/low/high, custom DCF assumptions, Graham number, DDM when applicable, composite fair value, margin of safety, recommendation, analyst target range, and MiFID II disclaimer.
- Cash-generation section shows FCF TTM/latest annual, FCF history, positive-FCF years, FCF growth, FCF margin when available, and DCF eligibility/data gaps.
- Earnings section shows revenue, net income, EPS, earnings history/trend, earnings growth, and quality notes where data is available.
- Balance-sheet and debt section shows total debt, cash, net debt, debt-to-equity, current ratio, quick ratio when available, interest coverage when available, and trend charts.
- Historical graphs section uses Recharts to show:
  - Earnings history: revenue, net income, EPS, and FCF over annual periods.
  - Debt history: total debt, cash, net debt, and debt-to-equity over time.
  - ROI/ROIC history: return on invested capital over time, labelled as ROIC when the API supplies ROIC rather than generic ROI.
  - ROE history: return on equity over time.
  - Each chart includes source badge, latest data date, unavailable-series handling, responsive desktop/mobile layout, and readable axis/tooltips.
- Dividend section shows dividend yield, dividend history, streak, payout ratio, FCF payout/coverage, dividend CAGR, and dividend sustainability status.
- Quality and growth section shows ROIC, ROE, gross/operating/net margins when available, revenue/FCF/EPS CAGR at 3y/5y/10y, and peer/sector context.
- Risk and data-quality section lists unavailable metrics, stale inputs, provider fallbacks, plan restrictions, and model caveats in plain language.
- Custom DCF controls open inline on the review page.
- Actions: add to watchlist, add to portfolio (visible but disabled with "coming soon" label until H4B completes), open inline custom DCF controls, refresh/seed symbol if allowed, and return to Screener/Security Detail.
- Data composition: H4A composes existing H4 endpoints on the frontend; a dedicated backend review endpoint is deferred to Phase H4C.
- Includes empty, loading, partial-data, stale-data, unavailable, and error states.
- Acceptance checklist:
  - `/securities/:symbol/review` renders as a standalone page, not a modal or hidden tab.
  - The page displays DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, and unavailable-data labels.
  - The page includes historical graphs for earnings, debt, ROI/ROIC, and ROE.
  - The page displays source coverage and freshness for FMP/Yahoo Finance/Mixed data.
  - The page can be opened from Screener, Security Detail, Watchlist, Portfolio, and Seed results.
  - Non-admin investors can open the page for seeded symbols.

### Phase H4B: Review Page — Portfolio-Add Integration
- Assess and implement the add-to-portfolio action on the In-Depth Review page.
- Requires the portfolio CRUD API (F2) and portfolio UI contract (H5) to be available.
- Replace the disabled "coming soon" add-to-portfolio button with a functional action that adds the symbol to a user-owned portfolio.
- Reuse the existing portfolio API client and mutation patterns from H5.
- Validate that adding from the review page does not bypass ownership rules or create duplicate holdings.

### Phase H4C: Review Page — Backend Review Endpoint
- Implement a dedicated backend endpoint (e.g. `GET /api/v1/securities/{symbol}/review`) that assembles the full single-stock research packet in one call.
- Replaces the frontend composition of multiple H4 endpoints with a single optimised server-side aggregation.
- Motivated by latency or fragility observed during H4A frontend composition.
- Response includes: profile, financials, ratios, financial health, valuation, dividends, growth, peers, score, source coverage, and freshness metadata.
- Update `SecurityReviewPage` to consume the new endpoint instead of composing individual calls.
- Preserve all existing research-packet sections, charts, and data-quality labels.

### Phase H5: Portfolio Builder UI
- Budget + risk profile + yield target inputs
- Proposed allocation table with editable weights
- Real-time constraint validation (sector, stock, country %)
- Donut chart of sector allocation
- Save portfolio button

### Phase H6: Watchlist & Alerts UI
- Watchlist table with MoS badge (color coded: green > 15%, yellow 5–15%, red < 5%)
- Alert threshold configuration inline
- Active alerts panel (dismissable)

### Phase H7: Dashboard
- Portfolio summary: total value, MoS average, yield
- Top movers in portfolio (% change)
- Active alerts summary
- Upcoming earnings + dividend calendar (next 30 days)

### Phase H8: Seed & Shared Universe UI
- Authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users can seed symbols into the shared research universe.
- Requires backend/API authorization support for limited non-admin seeding: investors/advisors can seed custom ticker lists, while admin-only controls remain available for named packs, quota/cost governance, and universe maintenance.
- The UI must make the scope explicit: seeding creates or refreshes platform-wide securities, fundamentals, ratios, quotes, valuations, and scores; it does not create personal watchlist or portfolio entries.
- `INVESTOR` and `ADVISOR` users can seed ticker CSV lists for research and then add seeded securities to their own watchlists or portfolios.
- `ADMIN` users can also seed named packs and perform broader shared-universe maintenance.
- Supports ticker CSV input for custom lists.
- Supports named seed packs for common markets or strategies, at minimum: default starter universe, US large-cap quality, dividend candidates, and value shortlist; pack availability can be admin-only if cost/quota controls require it.
- Shows a pre-submit preview of the symbols that will be seeded, including duplicate removal and normalized uppercase tickers.
- Shows seed status per symbol after submission: seeded, refreshed, skipped, failed, or unavailable on current data plan.
- Shows data source per symbol with explicit provider badges: `FMP`, `Yahoo Finance`, or `Mixed`.
- Shows data source by data category when available: profile, fundamentals, ratios, quote, dividends, valuation, and score.
- Shows fallback details when Yahoo Finance is used: FMP quota exceeded, FMP unavailable, FMP key missing, provider plan restriction, or symbol not available from FMP.
- Shows source freshness for seeded data: provider, last refreshed timestamp/date, and stale/unavailable state.
- Explains source limitations inline: FMP is the production primary source; Yahoo Finance is a fallback and may provide less complete fields for bulk screening, dividends, liquidity, and detailed financial health metrics.
- Shows result columns per symbol: ticker, company name, sector, exchange, country when available, company description/profile excerpt, current price, fair value, MoS, recommendation, and error detail.
- Successful seeded rows link directly to Security Detail.
- Failed rows keep the original ticker visible and show an actionable error message without blocking successful rows.
- Provides a handoff to Screener for market-wide research after seeding.
- Provides a handoff to the In-Depth Review page so the user can verify the complete single-stock research packet for a seeded symbol.
- Confirms seeded securities are discoverable by all authenticated users through search, screener, security detail, watchlist add, and portfolio add flows, while watchlists and portfolios remain user-owned.
- Includes empty, loading, partial-success, full-success, and failure states.
- Includes a MiFID II decision-support disclaimer wherever fair value, MoS, recommendation, or score outputs are shown.
- Acceptance checklist:
  - Investor can seed a CSV ticker list.
  - Advisor can seed a CSV ticker list.
  - Admin can seed a CSV ticker list.
  - Admin can seed a named pack; investors/advisors can seed named packs only when enabled by quota/cost policy.
  - Each seeded row shows whether data came from FMP, Yahoo Finance, or mixed provider coverage.
  - Fallback rows explain why Yahoo Finance was used instead of FMP.
  - Seeded symbols appear in the market-wide Screener/Search UI with description/profile context.
  - Any authenticated non-admin user can open a seeded symbol's Security Detail page.
  - In-Depth Review page exposes DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, and data-unavailable labels.

---

## Group HD — Full Demo Assessment

Goal: assess the completed frontend MVP as a full clickable product demo before moving into formal quality and observability work. This group verifies that the application feels coherent end to end, that the newest H8 Seed & Shared Universe UI fits the product, and that visible rough edges are captured or fixed before the Production Ready milestone.

### Phase HD1: Full Demo UI Assessment
- Run the localstack/full-demo environment and React frontend against deterministic local data.
- Walk through the complete authenticated user journey: login, dashboard, seed universe, screener, security detail, in-depth review, watchlist, portfolio builder, rebalancing, and alerts.
- Specifically verify the H8 Seed & Shared Universe UI:
  - CSV preview, duplicate removal, invalid ticker feedback, and submission states.
  - Admin named-pack seeding visibility and non-admin hiding behavior.
  - Source badges, fallback messaging, freshness labels, partial-success rows, failed-row errors, and handoffs to Screener, Security Detail, and In-Depth Review.
  - Clear explanation that seeding creates shared reference data and does not create personal watchlist or portfolio entries.
- Assess look and feel across all primary React surfaces:
  - Visual hierarchy, spacing, density, table readability, forms, badges, buttons, focus states, loading states, empty states, error states, and mobile/desktop responsiveness.
  - Consistency of navigation labels, route transitions, page headings, action placement, and decision-support disclaimers.
  - No text overlap, cramped controls, misleading color-only states, or marketing-style pages where an operational workflow is expected.
- Capture a concise assessment report under `specs/YYYY-MM-DD-full-demo-assessment/` with findings grouped as blockers, polish fixes, accessibility issues, copy issues, and deferred improvements.
- Fix low-risk visual and copy issues immediately when they are clearly scoped and do not change backend behavior.
- Defer larger UX or product changes into explicit follow-up roadmap items rather than silently expanding this phase.

### Phase HD2: Demo Polish Pass
- Apply the scoped polish fixes identified in HD1 across the React frontend and static demo pages where appropriate.
- Close the open INGR review-page findings from `specs/2026-06-28-full-demo-assessment/ingr-review-bug-notes.md`:
  - Keep the Docker backend image buildable from a Windows checkout by normalizing/executing the Maven wrapper in the Docker build stage or by enforcing repository line endings.
  - Make reseeding idempotent for INGR-style review data: no duplicate current-year fundamentals, duplicate current-date ratios, or retained stale current rows after refresh.
  - Normalize percentage display on the review page so decimal ratios such as dividend yield, payout ratio, ROE, ROIC, margins, and debt ratios render with correct units while already-percent values such as MoS remain correct.
  - Refresh or update watchlist state after `Add to watchlist` succeeds so the button immediately becomes the guarded `Already on watchlist` state and cannot produce a duplicate `409`.
  - Clear contradictory portfolio-add state after a successful add so users see either the success state or the existing-holding state, not both at once.
  - Remove Recharts container sizing warnings on the review page and verify charts remain visible across desktop and mobile layouts.
- Improve local demo readiness: documented startup steps, seeded credentials, demo URLs, known limitations, and a short checklist for stakeholder walkthroughs.
- Verify that the full demo can be run without live FMP/Yahoo calls or secrets using deterministic localstack data.
- Run frontend typecheck/build, backend compile/tests where supported by the environment, and `git diff --check`.
- Acceptance checklist:
  - A stakeholder can follow the documented local demo flow without command-line knowledge after the server is running.
  - The newest Seed Universe workflow is visible in the React app and its backend endpoint is represented accurately.
  - Core pages feel like one product: consistent spacing, labels, actions, badges, disclaimers, and error handling.
  - INGR review-page charts do not show duplicate current-year/current-date points after repeated reseeding.
  - Review-page percentage metrics show correct human percentages with no decimal/percentage-point mixups.
  - Review-page watchlist and portfolio actions transition to stable post-success states without avoidable duplicate API errors.
  - The Docker full-demo stack builds and starts on the Windows development checkout.
  - Any remaining UX gaps are documented with severity and owner/phase recommendation.

### Phase HD3: Beta Tester Persona Simulation
- Run a structured beta-test pass with three scripted investor personas after HD2 polish is complete.
- Each beta-tester agent must use the platform as a real evaluator would: discover candidate stocks, seed or open symbols as needed, inspect review/security detail pages, build a model portfolio, create a watchlist, and document platform impressions.
- Agent 1: Very prudent value investor.
  - Profile: low-risk, conservative, strictly margin-of-safety driven, skeptical of optimistic model assumptions.
  - Research source: summarized Seeking Alpha-style article inputs supplied as test fixtures or human-provided summaries; do not depend on scraping paywalled content during automated tests.
  - Workflow: identify conservative candidates, require strong margin of safety before portfolio inclusion, build a small defensive portfolio, and create a watchlist for stocks that are not cheap enough yet.
- Agent 2: Hedge-fund asset allocator.
  - Profile: professional allocator for a large equity hedge fund, focused on high margins, quality, scalability, and portfolio concentration risk.
  - Research source: Morningstar-style article/analyst-note summaries supplied as fixtures or human-provided notes; use them to select stocks for deeper platform analysis.
  - Workflow: compare high-margin businesses, review valuation and quality metrics, build a higher-conviction portfolio, and maintain a watchlist for candidates awaiting better valuation or cleaner data.
- Agent 3: Financial journalist / trend observer.
  - Profile: market-as-voting-machine user who starts from current news, trends, narratives, and price momentum rather than fundamental value discipline.
  - Research source: Google News-style headlines or human-curated news summaries; avoid relying on live news access for deterministic validation unless explicitly configured.
  - Workflow: choose trending stocks from news prompts, test whether the platform helps challenge or validate the narrative, build a news-driven model portfolio, and create a watchlist for fast-moving stories.
- Each agent produces a report under `specs/YYYY-MM-DD-beta-tester-personas/` containing:
  - Persona assumptions, source summaries used, and candidate-stock selection rationale.
  - Final portfolio with holdings, weights or quantities, valuation context, and key risks.
  - Watchlist with monitoring rationale, target signals, and why each symbol was not added to the portfolio.
  - Impressions on platform usability, trust, clarity of data gaps, usefulness of review pages, portfolio workflow, and watchlist workflow.
  - Prioritized improvement recommendations grouped as blockers, product gaps, UX polish, data-quality concerns, and nice-to-have enhancements.
- Acceptance checklist:
  - All three reports are reproducible from documented source summaries and seeded demo data.
  - Each report includes both portfolio and watchlist outputs.
  - The personas surface different product needs rather than repeating the same value-investor workflow.
  - Any new bugs or UX issues discovered are added to follow-up roadmap items with severity and recommended owner/phase.

### Phase HD4: Beta-Driven Feature Selection And Implementation
- Review the three HD3 beta-tester reports and extract candidate product improvements, grouped by persona, workflow, severity, expected user value, implementation complexity, and dependency risk.
- Use `specs/2026-06-28-hd3-beta-tester-personas/extracted-roadmap-requirements.md` as the initial HD4 backlog.
- Decide which new features should be added before Quality & Observability, explicitly separating:
  - Must-fix usability or trust blockers discovered during beta testing.
  - High-value feature additions that improve portfolio construction, watchlist monitoring, stock discovery, review-page decision support, or source transparency.
  - Deferred ideas that belong in later production-readiness, observability, identity, cloud, or commercial phases.
- Evaluate these HD3-derived candidate requirements explicitly:
  - Score availability transparency across seed results, review pages, screener/search rows, and portfolio holdings.
  - Portfolio concentration warnings in portfolio detail and add-to-portfolio flows.
  - Watchlist research rationale notes, including explicit "wait for better price" monitoring.
  - Screener empty-state diagnostics that identify restrictive filters and suggest relaxations.
  - Cross-symbol comparison for MoS, value score, quality, leverage/liquidity, growth, dividend indicators, and source/data coverage.
  - Story-versus-fundamentals review support using curated summaries or saved research notes rather than nondeterministic live news in demos.
  - Data-quality classification that distinguishes provider limitation, stale data, missing seeded history, missing internal computation, and calculation guardrail failure.
  - Persona replay scripts for repeatable seed, review, portfolio, watchlist, and evidence-capture flows.
- Create a short feature-selection report under `specs/YYYY-MM-DD-beta-feature-selection/` containing:
  - Candidate feature backlog with rationale.
  - Chosen feature set for implementation.
  - Rejected or deferred items with reasons.
  - Acceptance criteria and validation approach for each chosen feature.
- Implement the selected features as scoped increments, each with frontend, backend, persistence, or documentation changes only where required by the chosen feature.
- Preserve the decision-support boundary: new features may help research, compare, monitor, and document reasoning, but must not present outputs as personalised investment advice or order recommendations.
- Run the local full demo after implementation and re-check the three persona workflows where impacted.
- Acceptance checklist:
  - HD3 report findings are traceable to accepted, rejected, or deferred decisions.
  - Each HD3 extracted requirement is marked implemented, deferred to a named phase, or rejected with rationale.
  - Every implemented feature has validation evidence and updated user-facing documentation or demo notes when relevant.
  - No selected feature silently expands into unrelated platform redesign.
  - Any remaining beta-driven gaps are placed into later roadmap phases with a clear reason.

---

## Group I — Quality & Observability

### Phase I1: Test Coverage
- Unit tests: all Calculator classes (DCF, Graham, DDM, ValueScore)
- Integration tests: Screener API, Valuation endpoint, Auth flow
- Integration tests for any HD4-selected beta-driven workflows, including score/data-quality states, concentration warning thresholds, watchlist rationale persistence, and screener empty-state diagnostics.
- Deterministic persona replay tests or scripts for the three HD3 user journeys where practical.
- Test DB: H2 or Testcontainers PostgreSQL

### Phase I2: Observability
- Prometheus metrics via Micrometer: FMP API call count/latency, cache hit rate, screener latency
- Structured JSON logging (Logback)
- FMP quota monitoring metric (calls consumed vs plan limit)
- Spring Boot Actuator endpoints exposed to internal network only

---

## Group J — Google Account Sign-In (Phase J1 complete)

Goal: let a user sign in with a Google account while keeping the platform's existing local JWT, role, ownership, and refresh-token model unchanged. Google is used only to prove identity through OpenID Connect; the platform remains the authorization authority.

> **Priority note:** J1 (backend) is complete. J2 and J3 are deprioritized — they resume after Group MA. The analytical engine (VM, SR, MA) must be trustworthy before investing further in identity UI and test infrastructure.

### Phase J1: Google OpenID Connect Backend *(complete)*
- Add Spring Security OAuth2 Client support for Google Authorization Code + OpenID Connect login; use `openid`, `email`, and `profile` scopes only.
- Expose `GET /oauth2/authorization/google` as the login entry point and configure the callback at `/login/oauth2/code/google`; make callback and authorization paths public while all `/api/**` routes retain their existing JWT protection.
- Register Google client configuration exclusively through environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and an allow-listed application redirect URI. Add placeholders and setup guidance to `.env.example`; never commit a client secret.
- Validate the ID token issuer, audience/client ID, signature, expiry, nonce/state, and `email_verified=true`. Use Google's immutable `sub` claim as the provider identity; do not treat a mutable display name as an identifier.
- Persist an OAuth identity (`provider=GOOGLE`, `providerSubject`) linked to the existing `User` record, with uniqueness enforced on `(provider, providerSubject)` and normalized unique user email. Add a Flyway migration and repository support.
- On first successful sign-in, create an `INVESTOR` user only when the verified email is not already registered. For an existing password user with the same verified email, link the Google identity to that user without creating a duplicate. Google sign-in must never grant or change `ADMIN` or `ADVISOR` roles.
- After successful Google authentication, issue the same RS256 access and refresh tokens used by `POST /auth/login`, then redirect to the configured frontend callback without exposing tokens in query parameters; use the existing httpOnly refresh-cookie pattern and a short-lived, single-use handoff mechanism for the access token.
- Preserve existing username/password login, refresh, logout, JWT revocation, and demo-profile behavior.

---

## Group VM — Valuation Model Depth

Goal: strengthen the valuation engine so that its outputs are defensible, transparent, and trustworthy for real investment decisions. Currently the DCF engine accepts WACC without guidance, the composite hides terminal-value dominance, and no alternative conservative model (EPV) exists. This group fills those gaps so every downstream score, recommendation, and MoS is grounded in auditable assumptions.

Source: `specs/value-investor-roadmap-review.md` — items 1.1, 1.2, 1.3, 1.4, 1.5, 1.6.

### Phase VM1: Valuation Engine Backend Enhancements
- **WACC Calculator:** add `WaccCalculator.compute(symbol) → WaccResult` that derives a defensible default WACC from: risk-free rate (configurable, default 10Y US Treasury yield from provider data or manual override), equity risk premium (configurable, default 5.5%), beta (from FMP or Yahoo), cost of debt (interest expense / total debt), debt/equity ratio, and effective tax rate. Persist `WaccResult` alongside `ValuationResult` so the assumptions are traceable. Fall back to a sector-median WACC when beta or debt data is unavailable.
- **DCF Sensitivity Engine:** add `DcfSensitivityService.analyze(DcfInput) → DcfSensitivityResult` that computes a sensitivity matrix: fair value across 3–5 WACC values × 3–5 terminal growth rates. Include terminal value as a percentage of total DCF in every `DcfResult`. Flag when terminal value exceeds 70% with a `highTerminalDependence` boolean.
- **Earnings Power Value:** add `EpvCalculator.calculate(EpvInput) → BigDecimal` — normalized current earnings power (adjusted net income averaged over 5–7 years to smooth cycles), divided by WACC, minus net debt, divided by shares. EPV assumes zero growth; it provides a conservative floor valuation. Add RULE-08 guard: EPV skipped if fewer than 5 years of earnings history.
- **Owner Earnings:** add `OwnerEarningsCalculator.calculate(netIncome, depreciation, maintenanceCapex) → BigDecimal`. Estimate maintenance capex as a configurable percentage of depreciation (default 70%) when not separately reported. Expose owner earnings on the financials and review endpoints alongside FCF.
- **Graham Criteria Checklist:** add `GrahamCriteriaService.evaluate(symbol) → GrahamChecklistResult` that tests Graham's original criteria individually: P/E < 15, P/B < 1.5, P/E × P/B < 22.5, current ratio > 2.0, no negative earnings in last 5 years, 10-year earnings stability (no year-over-year decline > 33%), positive EPS growth over 10 years, and dividend record ≥ 10 years. Return each criterion as pass/fail/insufficient-data with the actual value. Persist alongside `ValuationResult`.
- **Composite Weight Configurability:** make composite weights (DCF/Graham/DDM) configurable per user or per request with validation (weights must sum to 100). Persist user preferences. Auto-reduce DCF weight when `highTerminalDependence` is true (shift excess to Graham/DDM proportionally).
- Flyway migration for `wacc_result` columns, `graham_checklist_result` columns or table, EPV columns on `valuation_result`, and user composite-weight preferences
- Unit tests: WACC calculation with known inputs, sensitivity matrix dimensions, EPV with averaged earnings, owner earnings formula, Graham checklist pass/fail per criterion, composite weight rebalancing

### Phase VM2: Valuation Transparency Frontend
- **DCF Sensitivity Table:** on the review page and valuation tab, display a WACC × terminal growth matrix (color-coded: green where fair value > current price by 15%+, yellow 0–15%, red where overvalued). Show the base-case cell highlighted.
- **Terminal Value Warning:** when terminal value exceeds 70% of total DCF, display a prominent label: "This valuation depends heavily on long-term assumptions (terminal value = X% of total)." Explain in plain language what this means.
- **WACC Transparency Panel:** show the computed WACC and every input (risk-free rate, ERP, beta, cost of debt, D/E ratio, tax rate) with source badges. Allow the user to override any input and recompute in real time.
- **EPV Display:** add EPV to the valuation section on the review page as "Conservative Floor (zero growth)" alongside DCF and Graham. Show the normalized earnings figure and years averaged.
- **Owner Earnings:** display owner earnings alongside FCF in the cash-generation section of the review page with the maintenance capex assumption shown.
- **Graham Criteria Checklist:** add a dedicated checklist card on the review page showing each Graham criterion with pass/fail/no-data icons and the actual value. Display "X of Y criteria met" summary.
- **Composite Weight Controls:** allow the user to adjust DCF/Graham/DDM/EPV weights via sliders on the valuation tab; recompute composite in real time; show how composite fair value changes as weights shift.
- Acceptance checklist:
  - WACC is computed with transparent, auditable inputs and can be overridden
  - Sensitivity table shows fair value across at least 9 WACC × growth combinations
  - Terminal value percentage is visible and flagged when dominant
  - EPV provides a zero-growth floor that is visually distinct from DCF
  - Graham Criteria Checklist shows individual pass/fail, not just the Graham Number
  - Composite weights can be adjusted and the platform auto-reduces DCF weight when terminal dependence is high

---

## Group SR — Scoring & Risk Intelligence

Goal: make the scoring engine sector-aware, add fundamental risk indicators (Piotroski, Altman, accruals), and prevent overvalued stocks from scoring high. The current fixed-weight formula penalizes non-dividend payers, ignores cyclicality, and allows a stock with negative MoS to still receive a respectable score. This group makes scoring trustworthy across sectors and market conditions.

Source: `specs/value-investor-roadmap-review.md` — items 2.1, 2.2, 2.3, 2.4, 4.4.

### Phase SR1: Scoring & Risk Backend
- **MoS Gate Rule:** add RULE-09: if margin of safety is negative (stock is overvalued relative to composite fair value), cap total ValueScore at 40 regardless of other sub-scores. A value investing platform must never rank an overvalued stock highly. Persist the gate-applied flag on `ValueScore`.
- **Sector-Adaptive Weights:** define weight profiles per sector category (at minimum: dividend-paying, non-dividend growth, REIT/utility, financial, cyclical). For non-dividend payers, redistribute the Dividend 10% weight proportionally to Quality and Growth. For REITs, adjust Safety sub-score to use FFO-based metrics instead of standard debt ratios. Weight profiles configurable via `application.yml`. Display which profile was applied.
- **Piotroski F-Score:** add `PiotroskiService.compute(symbol) → PiotroskiResult` using FMP's Piotroski data when available; compute from fundamentals when FMP data is missing. Return 9-factor score (0–9) with individual factor pass/fail. Persist `PiotroskiResult`. Add `GET /api/v1/securities/{symbol}/piotroski` endpoint. Add F-Score as a screener filter (`piotroskiMin`, `piotroskiMax`).
- **Altman Z-Score:** add `AltmanZScoreService.compute(symbol) → AltmanResult` implementing the original Z-Score formula for manufacturing (and Z''-Score for non-manufacturing/service). Return score value, zone classification (safe > 2.99, grey 1.81–2.99, distress < 1.81), and individual component values. Persist. Add `GET /api/v1/securities/{symbol}/altman` endpoint. Add Z-Score zone as a screener filter.
- **Cyclicality Detection:** add `CyclicalityService.assess(symbol) → CyclicalityResult` that analyzes 10-year earnings and revenue volatility. Compute coefficient of variation for revenue and earnings; classify as stable, moderate, or highly cyclical. For highly cyclical stocks, compute normalized earnings (10-year average) and a cycle-adjusted P/E. Flag in `ValuationResult` when valuation is based on peak or trough earnings.
- **Earnings Quality Ratio:** add `EarningsQualityService.compute(symbol) → EarningsQualityResult` computing: FCF/Net Income ratio (>1.0 = strong, 0.8–1.0 = acceptable, <0.8 = weak), Sloan accruals ratio ((net income − CFO) / total assets), and trend over 5 years. Flag when accruals are rising while FCF/income ratio is falling.
- Unit tests for MoS gate, each sector weight profile, Piotroski 9 factors, Altman formula (manufacturing and service variants), cyclicality classification thresholds, and accruals calculation

### Phase SR2: Scoring & Risk Frontend
- **Score Breakdown with Gate:** on the review page and score display, show the MoS gate status. When the gate is active (negative MoS), display: "Score capped at 40 — stock appears overvalued relative to composite fair value" with a distinct visual treatment (e.g., amber border, strikethrough of raw score showing capped score).
- **Sector Weight Profile Badge:** show which weight profile was applied (e.g., "Non-Dividend Growth Profile: Quality 30, Safety 23, Growth 22, MoS 25") and why. Allow the user to switch profiles manually for comparison.
- **Piotroski F-Score Card:** add a card on the review page showing the 9-factor breakdown with pass/fail per factor, total score (0–9), and a brief interpretation (strong ≥ 7, moderate 4–6, weak ≤ 3). Add F-Score column to screener results. Add F-Score to the comparison view.
- **Altman Z-Score Card:** add a card on the review page showing Z-Score value, zone (safe/grey/distress with color coding), component values, and formula variant used. Add zone column to screener results.
- **Cyclicality Indicator:** on the review page, show cyclicality classification (stable/moderate/highly cyclical) with the coefficient of variation. For cyclical stocks, show normalized earnings alongside reported earnings and flag: "Current earnings may be above/below the 10-year average — consider cycle position before relying on P/E or MoS."
- **Earnings Quality Section:** on the review page cash-generation section, add FCF/income ratio trend chart, accruals ratio, and quality classification (strong/acceptable/weak). Flag declining quality with a caution indicator.
- Add Piotroski, Altman zone, cyclicality, and earnings quality to the cross-symbol comparison view
- Acceptance checklist:
  - An overvalued stock (negative MoS) never scores above 40
  - Non-dividend payers are not structurally penalized in the total score
  - Piotroski F-Score is visible on review page and filterable in screener
  - Altman Z-Score flags distress risk before the user relies on a low P/E
  - Cyclical stocks display normalized earnings and cycle-position context
  - Earnings quality is computed and declining quality triggers a visible caution

---

## Group MA — Moat & Business Quality Analysis

Goal: add competitive advantage assessment and management quality signals so the platform can distinguish businesses worth owning long-term from temporarily cheap stocks. Value investing is about buying great businesses at fair prices — without moat analysis, the platform only addresses the "fair price" half. This group adds ROIC consistency analysis, capital allocation tracking, historical valuation bands, shares outstanding trends, and long-term stability scoring.

Source: `specs/value-investor-roadmap-review.md` — items 4.1, 4.2, 4.3, 4.5, 8.3.

### Phase MA1: Moat & Quality Backend
- **ROIC Consistency Analysis:** add `MoatAssessmentService.analyze(symbol) → MoatResult` that computes: 10-year ROIC series, ROIC consistency (percentage of years ROIC > estimated WACC), ROIC trend (improving/stable/declining via linear regression slope), average ROIC spread over WACC, and reinvestment rate (capex + R&D − depreciation) / NOPAT. Classify moat strength: wide (ROIC > WACC for 8+ of 10 years with stable/improving trend), narrow (5–7 years), or none (< 5 years). Persist `MoatResult`.
- **Capital Allocation Tracker:** add `CapitalAllocationService.analyze(symbol) → CapitalAllocationResult` that computes: shares outstanding trend over 10 years (net buyback or dilution percentage), total shareholder yield (dividend yield + net buyback yield), insider ownership percentage (from FMP insider data), acquisition spending as percentage of FCF (when available). Flag: "net diluter" (shares growing > 2% annually), "disciplined capital allocator" (shares flat/declining + dividend growth), or "empire builder" (heavy acquisition spending with declining ROIC).
- **Historical Valuation Bands:** add `ValuationHistoryService.compute(symbol) → ValuationBandResult` that computes 5-year and 10-year percentile bands for P/E, P/B, EV/EBITDA, and dividend yield. Return current value, median, 25th/75th percentiles, and where today's value sits within the band. Flag when current valuation is above the 75th percentile ("historically expensive") or below the 25th percentile ("historically cheap").
- **Long-Term Stability Scoring (Graham Stability):** add `StabilityService.assess(symbol) → StabilityResult` that tests: no negative annual EPS in last 10 years, no year-over-year EPS decline > 33%, positive revenue growth over 10 years, positive EPS growth over 10 years, and dividend continuity ≥ 10 years. Return each criterion as pass/fail with actual values. This complements the Graham Criteria Checklist (VM1) with deeper stability focus.
- Add endpoints: `GET /api/v1/securities/{symbol}/moat`, `GET /api/v1/securities/{symbol}/capital-allocation`, `GET /api/v1/securities/{symbol}/valuation-bands`
- Include moat, capital allocation, and valuation band data in the review endpoint (`GET /api/v1/securities/{symbol}/review`) response
- Add moat strength and shares outstanding trend as screener filters
- Unit tests: ROIC consistency classification, shares outstanding trend calculation, valuation band percentile computation, stability criteria pass/fail

### Phase MA2: Moat & Quality Frontend
- **Moat Assessment Card:** on the review page, display moat classification (wide/narrow/none) with a badge, 10-year ROIC chart overlaid with estimated WACC line, ROIC consistency percentage, trend direction, and reinvestment rate. Explain in one line what the classification means: "Wide moat: ROIC has exceeded cost of capital in 9 of 10 years with a stable trend."
- **Capital Allocation Card:** on the review page, display: shares outstanding 10-year chart (normalized to year 1 = 100 for easy visual), net buyback/dilution percentage, total shareholder yield, insider ownership percentage, and capital allocator classification with badge. Chart should make dilution immediately obvious.
- **Historical Valuation Band Charts:** on the review page valuation section, display P/E and EV/EBITDA over 5–10 years as a band chart (25th–75th percentile shaded, median line, current value dot). Show whether today's valuation is historically cheap, normal, or expensive. Add these charts alongside the existing DCF/Graham/MoS outputs.
- **Stability Scorecard:** on the review page, add a compact scorecard showing how many stability criteria the stock passes (e.g., "4 of 5 Graham stability criteria met") with individual pass/fail. Link to the Graham Criteria Checklist (VM2) for the full picture.
- Add moat strength, capital allocator type, and historical valuation position to the cross-symbol comparison view
- Add moat and shares outstanding trend columns to screener results
- Acceptance checklist:
  - ROIC consistency chart clearly shows whether returns exceed cost of capital over time
  - Shares outstanding trend makes dilution or buyback patterns immediately visible
  - Historical valuation bands show whether today's price is historically cheap or expensive
  - Moat classification is visible in screener results for universe-level filtering
  - Stability criteria are individually visible, not hidden inside a composite score
  - Capital allocator classification flags empire builders and net diluters

---

## Group J (continued) — Google Sign-In UI & Validation

> Resumes J2 and J3 after the analytical engine (VM, SR, MA) is in place. J1 backend is already complete.

### Phase J2: Google Sign-In UI & Account Lifecycle
- Add a **Continue with Google** action to the React login page (H2) that starts `/oauth2/authorization/google`, plus clear loading, cancelled-login, denied-consent, and provider-unavailable states.
- Complete the frontend callback route: consume the one-time handoff, initialize the normal authenticated session, clear callback state from the address bar, and redirect to the originally requested protected route or dashboard.
- Show the authenticated user's name/email and Google-linked status in account settings; do not display or retain Google access tokens in browser storage.
- Provide an authenticated unlink action only when the user still has a usable local password credential; block unlinking the sole sign-in method with a clear explanation. Reauthentication may be required for sensitive account-linking changes.
- Keep logout provider-local: ending a platform session must revoke the platform refresh token and clear its cookies, but must not attempt to sign the user out of Google globally.

### Phase J3: Security, Integration & Operational Validation
- Unit-test identity resolution and linking: new verified Google user, existing email match, repeat login, unverified email, conflicting provider subject, and role-preservation cases.
- Integration-test the OAuth callback with a mocked OIDC provider/JWK set: valid login produces the platform JWT/session; invalid issuer, audience, signature, expired token, invalid state/nonce, or unverified email is rejected; callback paths do not weaken `/api/**` authorization.
- Add browser tests for Google-login initiation, callback success, cancelled/denied login, protected-route return, logout, and unlink safeguards. Continue testing password login and refresh/logout flows unchanged.
- Add structured security events and metrics for Google sign-in success/failure, account creation/linking, and callback-validation failures, without logging ID tokens, authorization codes, client secrets, or personally sensitive claims.
- Document Google Cloud Console setup, exact redirect URIs per environment, consent-screen requirements, secret rotation, local-development callback configuration, and incident response for a compromised OAuth client secret.

---

## Group JC — Job Control & Ingestion Testability

Goal: provide admin-level observability and runtime control over ingestion jobs and per-symbol ingestion events. Currently jobs can be triggered on demand but cannot be enabled/disabled individually, their per-symbol outcomes are invisible outside log files, and there is no way to run a scoped partial ingestion (e.g. one symbol, one exchange, or specific data types) for testing. This group fills those gaps so that every subsequent demo and validation phase can verify the data pipeline deterministically.

### Phase JC1: Job Run History & Ingestion Event API
- `GET /api/v1/admin/jobs` — list all registered jobs with cron expression, enabled/disabled status, and last run summary (status, timestamp, records processed, error count)
- `GET /api/v1/admin/jobs/{jobName}/history?page=&size=` — paginated `JobRunLog` entries for a job: status, start/end timestamps, records processed, records failed, error messages, data source used (fmp/yahoo/mixed)
- Add `IngestionEvent` entity persisted during each job run: job run ID, symbol, data type (profile/fundamentals/ratios/quote/dcf/dividend/insider), status (success/skipped/failed), error detail, source (fmp/yahoo), timestamp
- `GET /api/v1/admin/jobs/{jobName}/events?runId=&symbol=&status=` — queryable per-symbol ingestion event log; filterable by run ID, symbol, and status
- Flyway migration for `ingestion_event` table with indexes on `(job_run_id)`, `(symbol)`, and `(status)`
- Unit tests for event persistence and query filters

### Phase JC2: Job Scheduling Runtime Control
- `PUT /api/v1/admin/jobs/{jobName}/enabled` (body: `{ "enabled": true|false }`) — enable or disable an individual scheduled job at runtime; state persisted to the database so it survives application restarts; disabled jobs skip execution on their next cron trigger and log a `SKIPPED` status
- `PUT /api/v1/admin/jobs/{jobName}/cron` (body: `{ "cron": "0 0 2 * * *" }`) — update the cron expression for a job at runtime; validated as a legal cron expression before acceptance; change takes effect on the next scheduling cycle
- Enhance `POST /api/v1/admin/jobs/{jobName}/run` to accept optional scope parameters: `symbols` (CSV), `exchange` (single exchange code), `dataTypes` (CSV of profile/fundamentals/ratios/quote/dcf/dividend/insider); return a `jobRunId` so the caller can poll status and events
- `GET /api/v1/admin/jobs/runs/{jobRunId}/status` — poll a triggered run: status (RUNNING/SUCCESS/FAILED), symbols processed vs total, elapsed time, error count
- Update `feature-demo.html` and `full-demo.html` with panels for: job list with enable/disable toggles, job history table, ingestion event browser, and scoped job trigger form
- Integration tests: disabled job does not fire on cron; cron update changes next execution window; scoped trigger processes only the requested symbols; job run status transitions correctly from RUNNING to SUCCESS/FAILED

---

## Group PI — Portfolio Intelligence

Goal: add portfolio-level risk metrics, liquidity awareness, benchmark context, and practical rebalancing intelligence so the portfolio construction workflow produces results a real investor can act on. Currently the platform analyzes individual stocks thoroughly but the portfolio is just a list of holdings with weights and constraints — no portfolio-level risk, no liquidity check, no benchmark comparison, no tax or cost awareness in rebalancing.

Source: `specs/value-investor-roadmap-review.md` — items 5.1, 5.2, 5.3, 5.4, 5.5.

### Phase PI1: Portfolio Intelligence Backend
- **Portfolio Analytics Engine:** add `PortfolioAnalyticsService.analyze(portfolioId) → PortfolioAnalyticsResult` that computes:
  - Weighted-average MoS, P/E, dividend yield, value score, and Piotroski F-Score across holdings
  - Sector concentration map: weight per sector with flags when any sector exceeds 40%
  - Holding concentration: flag positions below 3% as "immaterial" and above 20% as "concentrated"
  - Weighted moat profile: percentage of portfolio in wide/narrow/no-moat stocks
  - Quality distribution: portfolio-level average ROIC, ROE, and earnings quality
  - Persist snapshot for historical tracking
- **Liquidity Assessment:** add `LiquidityService.assess(symbol, positionValue) → LiquidityResult` that computes: average daily volume × average price = average daily dollar volume; days to liquidate position = position value / (daily dollar volume × participation rate, default 10%); liquidity classification (liquid < 5 days, moderate 5–20 days, illiquid > 20 days). Flag in portfolio analytics when any holding is illiquid.
- **Benchmark Comparison:** add `BenchmarkService.compare(portfolioId, benchmarkSymbol) → BenchmarkComparison` that computes characteristic comparison (not returns tracking): portfolio weighted P/E vs. benchmark P/E, portfolio yield vs. benchmark yield, portfolio weighted MoS vs. benchmark (if applicable), sector weight difference map. Default benchmark: SPY. Configurable per portfolio.
- **Rebalancing Intelligence:** enhance `RebalancingService` to:
  - Distinguish "must rebalance" (constraint breach) from "could rebalance" (drift within tolerance band, configurable default ±3%)
  - Estimate round-trip transaction cost per trade (configurable cost model: flat fee or percentage, default 0.1%)
  - Flag short-term vs. long-term holdings based on acquisition date (user-entered or default to holding creation date)
  - Compute total estimated rebalancing cost and rank trades by urgency
  - Add a "minimum position size" constraint (configurable, default 3%) — warn but don't block positions below minimum
- Add endpoint: `GET /api/v1/portfolios/{id}/analytics`
- Enhance existing `GET /api/v1/portfolios/{id}/rebalance` response with cost estimates, urgency ranking, and holding-period flags
- Unit tests: weighted-average calculations, liquidity classification thresholds, constraint-breach vs. drift detection, transaction cost estimation

### Phase PI2: Portfolio Intelligence Frontend
- **Portfolio Analytics Dashboard:** on the portfolio detail page, add an analytics summary section:
  - Weighted-average metrics row: MoS, P/E, yield, value score, F-Score
  - Sector allocation donut chart with concentration warnings highlighted in amber/red
  - Holding concentration bar chart with immaterial (< 3%) and concentrated (> 20%) zones marked
  - Moat profile pie chart (wide/narrow/none distribution)
  - Quality distribution mini-chart (ROIC, ROE averages)
- **Liquidity Column:** add a liquidity indicator to the holdings table: green (liquid), yellow (moderate), red (illiquid) with "days to liquidate" tooltip. Flag illiquid holdings prominently.
- **Benchmark Comparison Panel:** add a side-by-side comparison panel: portfolio vs. benchmark on P/E, yield, sector weights, and quality metrics. Show the differential clearly (portfolio is cheaper/more expensive, higher/lower yield, more/less concentrated than benchmark).
- **Smart Rebalancing:** enhance the rebalance UI to show:
  - Urgency classification per trade: "must" (constraint breach, red) vs. "could" (drift, yellow) vs. "hold" (within tolerance, green)
  - Estimated transaction cost per trade and total rebalancing cost
  - Short-term / long-term holding flag per sell recommendation
  - Position size warnings for holdings below minimum threshold
  - A "rebalance preview" step before confirming that shows total cost, number of trades, and constraint resolution
- Acceptance checklist:
  - Portfolio detail page shows weighted-average MoS, yield, and quality metrics
  - Sector and holding concentration warnings are visible without opening a separate report
  - Illiquid holdings are flagged before the user increases position size
  - Benchmark comparison shows whether the portfolio is cheaper or more expensive than the market
  - Rebalancing distinguishes urgent constraint breaches from optional drift correction
  - Transaction cost estimates are visible before executing rebalance

---

## Group PW — Professional Workflow & Compliance

Goal: add the research audit trail, investment checklist framework, data cross-verification, intrinsic value confidence scoring, and ADVISOR role scoping so the platform is suitable for professional use and regulatory defensibility. Currently there is no timestamped record of what the platform showed when a user made a decision, no way for users to apply their own investment criteria, and no verification that provider data is correct.

Source: `specs/value-investor-roadmap-review.md` — items 3.1, 7.1, 7.2, 8.1, 8.2, 8.4.

### Phase PW1: Professional Workflow Backend
- **Research Decision Audit Trail:** add `ResearchSnapshot` entity that captures a timestamped record when a user takes a portfolio or watchlist action (add holding, add to watchlist, remove holding). Record: user ID, symbol, action type, timestamp, current price, composite fair value, MoS, value score, WACC used, data source active (fmp/yahoo/mixed), Piotroski F-Score, moat classification, and any user-entered rationale. Persist immutably (append-only, never updated or deleted). Add `GET /api/v1/audit/decisions?symbol=&from=&to=` endpoint (user sees own decisions only; admin can query all).
- **Investment Checklist Framework:** add `InvestmentChecklist` entity with user-defined criteria. Each criterion has: label (e.g., "ROIC > 15% for 5+ years"), type (quantitative with operator/threshold, or qualitative pass/fail), and evaluation method (auto-computed from platform data, or manual user entry). Add CRUD endpoints: `GET/POST/PUT/DELETE /api/v1/checklists`. Add `POST /api/v1/checklists/{id}/evaluate/{symbol}` that auto-fills quantitative criteria from platform data and returns a checklist evaluation with pass/fail/no-data per criterion. Persist checklist evaluations for audit trail.
- **Intrinsic Value Confidence Score:** add `ValuationConfidenceService.compute(symbol) → ConfidenceResult` that computes a confidence level (high/medium/low) based on: years of historical data available (10+ = high, 5–9 = medium, < 5 = low), DCF scenario spread (high − low) / base (< 20% = high, 20–40% = medium, > 40% = low), number of applicable valuation models (3+ = high, 2 = medium, 1 = low), data source completeness (all fields present = high, some missing = medium, major gaps = low), and earnings consistency (stable = high, moderate variation = medium, volatile = low). Return overall confidence and per-factor breakdown. Include in valuation endpoints and review response.
- **Data Cross-Verification Flags:** add `DataVerificationService.check(symbol) → VerificationResult` that flags data-quality concerns: fundamental data older than 90 days from expected filing date, shares outstanding discrepancy > 5% between quote and fundamental sources, EPS or revenue changing by more than 50% between consecutive quarters without a corresponding note, and missing or zero values in critical fields (EPS, book value, FCF, shares outstanding). Return flags per field. Include in review endpoint.
- **ADVISOR Role Scoping:** add documentation and UI copy clarifying that the ADVISOR role provides research and portfolio-modeling capabilities only — the platform does not perform client suitability assessments, best-execution obligations, or regulated investment recommendations. Add a prominent banner on portfolio and rebalancing pages when the user role is ADVISOR: "This tool supports your research process. Suitability assessment, client risk profiling, and regulatory record-keeping remain your responsibility." Persist the disclaimer acknowledgement per session.
- **Circle of Competence:** add `UserPreferences` fields for preferred sectors and competence-marked industries. Add `GET/PUT /api/v1/preferences/competence` endpoint. When set, screener and universe curation can optionally filter to competence sectors. Display a subtle badge on review pages when a stock is outside the user's marked competence.
- Flyway migration for `research_snapshot`, `investment_checklist`, `checklist_criterion`, `checklist_evaluation`, `user_preferences` tables
- Unit tests: audit snapshot immutability, checklist auto-evaluation with known inputs, confidence score computation, data verification flag triggers, competence filter application

### Phase PW2: Professional Workflow Frontend
- **Research Decision Timeline:** add a "Decision History" section accessible from portfolio detail, watchlist, and a dedicated `/audit` route. Show a chronological timeline of add/remove actions with the platform state at the time of decision: price, fair value, MoS, score, data source, and user rationale. Exportable as CSV for compliance record-keeping.
- **Investment Checklist Builder:** add a "My Checklist" page where users can create, edit, and manage their investment checklist criteria. On the review page, add an "Apply My Checklist" button that evaluates the current stock against the user's checklist and shows pass/fail per criterion. Auto-computed criteria show the platform value; manual criteria prompt the user for their assessment. Show "X of Y criteria met" summary.
- **Confidence Badge:** on the review page valuation section, display a confidence badge (high/medium/low with green/yellow/red) next to the composite fair value. Expand to show per-factor breakdown on click. Add confidence level to screener results as an optional column.
- **Data Verification Warnings:** on the review page, display inline warnings next to any field flagged by the verification service: "Fundamental data may be stale (last update X days ago)", "Shares outstanding differs between sources", or "EPS changed significantly — verify with SEC filing." Warnings are informational, not blocking.
- **ADVISOR Compliance Banner:** display the regulatory scope disclaimer on portfolio and rebalancing pages for ADVISOR users. Show once per session with an acknowledgement action; don't block workflow but ensure it's seen.
- **Circle of Competence Indicator:** on the review page header and screener results, show a subtle "outside your marked competence" indicator when a stock's sector is not in the user's competence list. Add competence filter toggle to screener. Add competence sectors editor in user settings.
- Acceptance checklist:
  - Every portfolio/watchlist action creates an immutable audit snapshot capturing platform state at decision time
  - Audit timeline is viewable and exportable for compliance
  - Users can create custom investment checklists with auto-evaluated and manual criteria
  - Intrinsic value confidence is visible and explainable next to every fair value output
  - Stale or suspicious data is flagged before the user relies on it for decisions
  - ADVISOR users see the regulatory scope disclaimer without workflow disruption
  - Circle of competence is optional and non-blocking — a nudge, not a gate

---

## Group SC — Seeds Choice & Universe Curation

Goal: replace manual ticker-list entry with a structured stock-selection process that narrows the investable universe to a manageable, high-quality set before analysis. The platform should help users decide *which* stocks to analyze, not just analyze whatever tickers the admin happens to type. This group adds universe filtering criteria, pre-built research universes, and a selection workflow that feeds into the existing seed pipeline.

### Phase SC1: Universe Selection Criteria & Filtering API
- `POST /api/v1/admin/universe/preview` — accepts filtering criteria and returns a preview of matching symbols *before* seeding:
  - `exchanges` (list): restrict to specific exchanges (e.g. NYSE, NASDAQ)
  - `countries` (list): restrict to specific countries
  - `sectors` (list): include or exclude specific sectors
  - `marketCapMin` / `marketCapMax`: market cap range filter
  - `volumeMin`: minimum average trading volume
  - `maxSymbols`: cap the number of symbols returned (default 100, max 500)
  - `sortBy`: market cap, volume, or alphabetical
- Preview response includes: total matches, returned symbols with company name / exchange / sector / market cap when available, and a warning when results are capped
- The filtering queries the existing FMP stock list endpoint or uses a locally cached symbol directory; does not require each symbol to be fully seeded first
- `POST /api/v1/admin/universe/seed` — accepts the same criteria and seeds all matching symbols through the existing pipeline (profile → fundamentals → ratios → quote → valuation → score)
- Add pre-built universe templates accessible via `GET /api/v1/admin/universe/templates`:
  - `us-blue-chip`: S&P 500 or large-cap US stocks with market cap > $10B
  - `dividend-aristocrats`: known dividend-growth stocks with 10+ year dividend streaks
  - `value-candidates`: stocks with P/E < 15, P/B < 1.5, and positive FCF
  - `defensive-quality`: consumer staples + healthcare + utilities with ROE > 15%
  - Custom templates configurable via `application.yml`
- Integration tests for preview filtering, symbol count capping, and template resolution

### Phase SC2: Universe Curation UI & Workflow
- Add a **Universe Curation** page or panel in the React frontend (accessible to `ADMIN` and optionally `ADVISOR`/`INVESTOR` with restrictions)
- **Filter builder:** exchange multi-select, country multi-select, sector multi-select, market cap range slider, volume minimum input, max symbols input
- **Template selector:** dropdown of pre-built templates from SC1; selecting a template pre-fills the filter builder
- **Preview step:** shows matching symbols in a table (symbol, company name, exchange, sector, market cap) with total count and cap warning; user reviews before committing
- **Seed action:** button to seed the previewed universe; shows progress with per-symbol status (same as H8 seed UI patterns); links to ingestion events from JC for detailed monitoring
- **Active universe summary:** shows the current seeded universe size, sector distribution, exchange distribution, and last refresh date
- **Restriction controls:** allow narrowing an already-seeded universe by marking symbols as excluded from screener results without deleting their data; excluded symbols remain in the database but are filtered out of screener queries and universe counts
- Acceptance checklist:
  - An admin can select a template, preview the matching universe, and seed it in a single workflow
  - The preview accurately reflects filter criteria before any data is ingested
  - The seeded universe appears immediately in the screener and search after seeding completes
  - Universe size is manageable: templates and filters prevent accidentally seeding thousands of symbols beyond the data plan capacity
  - Excluded symbols do not appear in screener results but retain their data for direct access via symbol search

---

## Group RD1 — Real Demo: Full Stack with Live Ingestion

Goal: demonstrate the entire platform end to end with real market data ingested on startup from Yahoo Finance (zero cost, no API key required). All features are activated — auth, ingestion, valuation, scoring, screener, security detail, review page, watchlist, portfolio, alerts, dashboard, and the job control from JC. Agent 1 (prudent value investor) walks through every major workflow and captures screenshots as stakeholder-presentable evidence.

### Phase RD1-1: Yahoo Finance Startup Ingestion Profile
- Create a `realDemo` Spring profile that activates all features with `MARKET_DATA_SOURCE=yahoo`
- On startup, automatically seed a curated ticker list from `REAL_DEMO_TICKERS` env var (default: `AAPL,MSFT,KO,JNJ,PG,PEP,WMT,BRK-B,UNP,XOM`) using the existing seed pipeline: profile → fundamentals → ratios → quote → valuation → score for each symbol
- Run a single pass of quote refresh, dividend update, and alert detection after seeding completes so that all data is current at first page load
- Log ingestion progress to `IngestionEvent` (from JC1) so the startup sequence is observable from the job control UI
- `docker-compose.realDemo.yml`: PostgreSQL + Redis + Spring Boot with `realDemo` profile; single `docker compose up` starts everything
- Admin user auto-seeded (same as demo profile); one `INVESTOR` test user auto-seeded for non-admin workflow testing
- Document startup steps, expected startup time, and known Yahoo Finance coverage limitations in a `scripts/real-demo-guide.md`

### Phase RD1-2: Agent 1 Full Feature Walkthrough & Screenshots
- Agent 1 (prudent value investor persona from HD3) executes a scripted walkthrough covering every major feature area:
  - **Auth:** login as admin, login as investor, token refresh, logout
  - **Dashboard:** verify portfolio summary, top movers, active alerts, upcoming events
  - **Seed & Universe:** seed additional symbols, verify source badges show Yahoo Finance, verify ingestion events in job control panel
  - **Job Control:** view job list with cron and enabled status, browse job run history, inspect per-symbol ingestion events, disable a job, re-enable it, trigger a scoped ingestion for a single symbol
  - **Screener:** apply Graham preset, apply conservative filters, inspect results with MoS badges and company descriptions
  - **Security Detail:** open AAPL — verify profile, financials, ratios, valuation, dividends, growth, insiders, peers tabs
  - **In-Depth Review:** open KO review page — verify DCF, FCF, Graham number, MoS, earnings, debt, dividend sustainability, historical charts, source coverage, data-quality labels
  - **Watchlist:** add JNJ and PG with rationale notes ("wait for better price"), verify alert thresholds, check active alerts
  - **Portfolio:** create a 5-stock defensive portfolio, run simulation, check concentration warnings, run rebalance
  - **Custom DCF:** run a custom DCF on MSFT with conservative assumptions, compare to composite valuation
- Capture a screenshot at each major step; store under `specs/YYYY-MM-DD-rd1-full-demo/screenshots/`
- Produce a walkthrough report under `specs/YYYY-MM-DD-rd1-full-demo/walkthrough-report.md` with: step, screenshot reference, observed result, pass/fail, and notes on data quality or Yahoo Finance limitations
- Acceptance checklist:
  - The platform starts with `docker compose up` and is ready for use within a reasonable time
  - All 10 seeded symbols have profile, fundamentals, ratios, quote, valuation, and score data from Yahoo Finance
  - Agent 1 completes the full walkthrough without encountering unrecoverable errors
  - Job control panels show ingestion history and per-symbol events
  - Screenshots are stakeholder-presentable evidence of a working product
  - Yahoo Finance data limitations are documented but do not block the core workflows

---

## Group L — Conservative Research Workflow Hardening

Goal: turn the HD3 Agent 1 prudent-value validation journal into repeatable product features and demo evidence. This group strengthens conservative investor workflows around 10-stock validation portfolios, "good business, wrong price" watchlists, score/data-quality confidence, and concentration-aware portfolio construction. It preserves the decision-support boundary: the platform documents research reasoning and risk signals, but does not recommend trades.

Source artifact: `specs/2026-06-28-beta-feature-selection/agent-1-prudent-validation-journal.md`.

### Phase L1: Prudent Persona Replay Pack
- Add a deterministic replay script or documented workflow for the Agent 1 10-symbol set: `BRK.B,JNJ,PG,KO,PEP,WMT,MSFT,ADP,UNP,XOM`.
- Seed the symbols, open each review packet, and capture score availability, valuation availability, source/freshness status, MoS, recommendation, and data-quality notes.
- Create a 10-position equal-weight validation portfolio and verify that no single holding breaches the holding concentration threshold.
- Add an oversized KO or JNJ scenario to confirm holding concentration warnings appear.
- Add PG, KO, JNJ, and MSFT to the watchlist with rationale notes and confirm persistence after reload.
- Store replay output under the relevant spec/demo evidence folder; do not describe the model as investable.

### Phase L2: Conservative Portfolio Review Pack
- Add a portfolio review surface or report section that summarizes conservative validation evidence: holding weights, sector weights, MoS, score availability, valuation availability, data-quality blockers, and watchlist rationale coverage.
- Flag incomplete validation when any holding is missing current price, sector, score status, or valuation status.
- Show conflicts between business quality and negative margin of safety, especially for defensive or high-quality symbols.
- Keep all copy factual and decision-support oriented; avoid buy/sell language.
- Include a printable or exportable journal-style summary suitable for stakeholder review.

### Phase L3: Availability Status Examples And Diagnostics
- Create deterministic demo fixtures or seeded examples for every availability state: `AVAILABLE`, `STALE`, `PENDING`, `PROVIDER_LIMITED`, `MISSING_SEEDED_HISTORY`, `MISSING_INTERNAL_COMPUTATION`, and `GUARDRAIL_BLOCKED`.
- Ensure review, screener rows, portfolio holdings, and watchlist-adjacent flows render each state consistently.
- Add tests for status mapping and UI rendering where practical.
- Document how each status should be interpreted by conservative users without turning the interpretation into investment advice.
- Feed any remaining gaps into Group I quality coverage and observability metrics.

### Phase L4: Conservative Workflow Enhancements
- Add a conservative research preset that combines positive MoS, score availability, dividend coverage, leverage/liquidity resilience, and data completeness.
- Add screener empty-state diagnostics for conservative filters, identifying which criteria likely eliminated candidates and suggesting relaxations while preserving the current criteria.
- Add selected-symbol comparison for the Agent 1 workflow: MoS, value score, quality, leverage/liquidity, growth, dividend indicators, and source/data coverage.
- Add saved research-note support beyond watchlist rationale only if the concise rationale field proves too small during replay.
- Acceptance checklist:
  - The Agent 1 journal findings are traceable to implemented features, deterministic replay evidence, or explicitly deferred follow-ups.
  - The 10-stock validation portfolio can be recreated from seeded/local data and produces concentration/data-quality evidence.
  - Watchlist rationale supports "wait for better price" and data-quality-gap workflows.
  - Every availability status has at least one deterministic example or a documented reason why it cannot yet be produced.
  - The feature set does not present the 10-stock model as personalised investment advice.

---

## Group RD2 — Real Demo: Curated Universe Validation

Goal: validate the seeds-choice process end to end by having Agent 1 use the SC universe curation workflow to build a focused research universe, then test all platform features against that curated set. This confirms that the filtering, seeding, and analysis pipeline works coherently when the user starts from universe selection rather than manual ticker entry.

### Phase RD2-1: Agent 1 Curated Universe Walkthrough & Screenshots
- Agent 1 (prudent value investor) executes a scripted walkthrough starting from universe curation:
  - **Universe Selection:** use the `defensive-quality` template, preview results, narrow by removing sectors with insufficient coverage, seed the curated universe (~20–30 symbols)
  - **Ingestion Monitoring:** observe seeding progress in the job control panel, verify per-symbol ingestion events, confirm all symbols have profile + fundamentals + quote data from Yahoo Finance
  - **Screener Research:** apply the conservative research preset (from L4 if available, or manual filters), sort by value score, identify top candidates
  - **Deep Analysis:** open the top 5 candidates on the in-depth review page; verify valuation, FCF, earnings, debt, dividends, historical charts, and data-quality labels
  - **Comparison:** compare the top 5 candidates on MoS, value score, quality metrics, and dividend indicators
  - **Portfolio Construction:** build a 5–8 stock defensive portfolio from the curated universe, run simulation, verify concentration warnings, run rebalance
  - **Watchlist:** add 3–5 "almost cheap enough" stocks with rationale notes explaining what price or event would trigger purchase
  - **Dashboard & Alerts:** verify dashboard reflects the new portfolio, check that alerts are configured for watchlist thresholds
- Capture a screenshot at each major step; store under `specs/YYYY-MM-DD-rd2-curated-demo/screenshots/`
- Produce a walkthrough report under `specs/YYYY-MM-DD-rd2-curated-demo/walkthrough-report.md` with: step, screenshot reference, observed result, pass/fail, comparison to RD1 findings, and notes on how universe curation improved or changed the research experience
- Acceptance checklist:
  - Universe curation workflow produces a focused, manageable research set without manual ticker entry
  - Agent 1 finds the curated universe more coherent for conservative research than an uncurated manual seed
  - All platform features work correctly against the curated universe
  - Data-quality and coverage gaps are documented but do not block core workflows
  - The walkthrough report is stakeholder-presentable and demonstrates the value of structured universe selection
  - Comparison with RD1 highlights what improved with universe curation vs. manual seeding

---

## Group RCL — Investor Replay Recycling

Goal: recycle issues discovered by autonomous investor-style market analysis before cloud distribution. This group turns real user-research friction into focused hardening work: screener result consistency, API input normalization, symbol canonicalization, and log-assisted defect triage. It preserves the decision-support boundary by improving evidence quality and workflow clarity without turning shortlist outputs into trade recommendations.

Source artifacts: Agent investor run on 2026-07-03, log-monitor baseline, L1/RD2 replay evidence, and generated `analysis-*` screenshots/JSON artifacts in the workspace root.

### Phase RCL1: Screener And Symbol Recycling Pass
- Normalize screener numeric threshold handling so API requests accept or explicitly reject fractional inputs (`0.15`) versus percentage inputs (`15`) with clear validation errors instead of `500` responses.
- Add regression coverage for conservative screener requests, empty payloads, fractional threshold payloads, and UI-standard percentage payloads.
- Resolve screener page inconsistency where `0 active filters` and `0 companyies found` can appear while the Agent 1 comparison table below is populated.
- Fix the screener empty-state copy typo (`companyies`) and ensure the empty-state explanation distinguishes "no screener results" from "comparison/watchlist candidates are still available".
- Audit the screener DOM structure for duplicate `<main>` landmarks and adjust layout semantics so accessibility tests and browser automation target one primary main region.
- Canonicalize Berkshire Hathaway class B symbols across seed, review, watchlist, comparison, and portfolio surfaces (`BRK-B` provider form versus `BRK.B` display/research form), including migration or alias handling for existing demo data.
- Add log-correlation evidence for the investor-reported issues: route, payload, timestamp, status code, exception/validation message, and frontend route.
- Acceptance checklist:
  - Invalid screener payloads return `400` with actionable field errors, not `500`.
  - Standard UI screener requests still return expected results for seeded demo data.
  - Screener empty state and Agent 1 comparison can coexist without contradictory copy.
  - Automated accessibility checks see one primary page landmark on the screener route.
  - `BRK.B`/`BRK-B` can be seeded, reviewed, watchlisted, compared, and held without missing-price or missing-review artifacts caused only by symbol format mismatch.
  - Investor replay and log-monitor reports are linked from validation evidence.

### Phase RCL2: Replay-To-Backlog Feedback Loop
- Add a repeatable two-agent validation protocol: investor agent explores market/app workflows and reports structured UI/API problems; monitor agent correlates Docker/backend/frontend logs and produces severity-tagged evidence.
- Store each replay cycle under `specs/YYYY-MM-DD-investor-replay-recycling/` with screenshots, request payloads, relevant log excerpts, shortlist rationale, and decision-support boundary notes.
- Create a lightweight triage template that classifies findings as data-quality gap, UI contradiction, API validation defect, provider limitation, accessibility issue, or product follow-up.
- Require every real-demo replay after RD2 to either close findings with validation evidence or carry them into a roadmap phase before GCP deployment.
- Acceptance checklist:
  - At least one replay run demonstrates investor-agent issue reporting and monitor-agent log correlation.
  - Findings include severity, affected route/API, reproduction path, and next owner.
  - No replay artifact describes a shortlist or demo portfolio as investable or as personalized advice.
  - Open findings are visible before K1 stakeholder cloud deployment begins.

### Phase RCL3: Security Detail Historical Chart And Data Verification Pass
- Verify `http://localhost:5173/securities/KO` and related review routes for historical chart readability: quote/history charts must display correctly labeled price data, readable axes, and no misleading flat lines caused by missing or repeated source values.
- Add a user-selectable history window for security-detail/review charts where appropriate (for example 1y, 3y, 5y, 10y, max), and preserve a sensible default when the available history is shorter than the selected range.
- Audit ratio, return, valuation, P/E, and capital-structure charts for repeated identical values. If true historical series are unavailable, do not graph synthetic repeated points; show the current value as text with an explicit unavailable-history note instead.
- Verify whether the Financial Health resilience indicator is incorrectly constant across symbols or periods; if it is derived from sparse data, expose the data basis and avoid implying a historical trend.
- Investigate the valuation action where clicking `Run FCF` does not display any visible result; ensure success, validation failure, guardrail-blocked, and provider-limited states all render user-facing feedback and are logged.
- Cross-check dividends, growth, and insider sections against persisted API responses and provider/source metadata so the UI does not display stale, repeated, or fabricated-looking values.
- Add regression tests or replay assertions for:
  - KO security-detail chart rendering with available quote/history data.
  - Missing historical ratios showing text-only current values instead of flat charts.
  - FCF valuation run feedback for success and guardrail-blocked cases.
  - Dividends, growth, and insider panels showing source/freshness/unavailable states consistently.
- Acceptance checklist:
  - Charts are only rendered when there is enough real historical data to support a chart.
  - Users can choose the history window where historical depth exists.
  - Repeated values are either confirmed as real source history or replaced by explicit text-only unavailable-history states.
  - FCF run produces visible feedback in every outcome.
  - Dividends, growth, and insider values are cross-checked against backend responses and documented in validation evidence.

### Phase RCL4: Beta Tester Functional Fix Pack
- Consolidate beta tester findings from the investor, advisor/compliance, UI/accessibility, and data-quality/API test passes into a single fix pack before GCP stakeholder deployment.
- Align Agent 1 comparison, screener results, portfolio review, and security review pages to a single clearly identified data snapshot or show explicit source/date differences when they intentionally diverge.
- Replace user-facing `Recommendation` terminology on review, screener, dashboard, portfolio, and API-derived UI labels with neutral decision-support wording such as `Model valuation status`, `Research signal`, or `Valuation classification`.
- Hide admin-only or restricted workflows such as Universe Curation from `INVESTOR` navigation, or render a clear access-denied page with disabled controls instead of raw `Forbidden` messages.
- Seed at least one non-destructive professional checklist for demo users, such as `Conservative quality review`, so checklist evaluation can be tested from KO/MSFT review pages without manual setup.
- Improve audit/decision-history completeness by capturing rationale, source action, route/context, and correlation identifier for portfolio/watchlist decisions where available; flag missing rationale in the UI/export instead of silently showing `null`.
- Stabilize chart containers and responsive layouts:
  - eliminate Recharts `width(-1)` / `height(-1)` warnings on review and portfolio routes;
  - prevent horizontal page overflow on portfolio mobile viewports around 390px wide;
  - ensure chart/table overflow is local and intentional where dense data cannot fit.
- Fix login first-visit messaging so an anonymous user does not see `Your session has expired` unless a previously authenticated session actually expired.
- Improve disabled-button states, including Seed Universe invalid input, so disabled primary actions are visually distinct and not mistaken for available commands.
- Add API/data-quality safeguards discovered by beta testing:
  - `POST /api/v1/screener` with `{}` or malformed numeric payloads returns defaults or `400` validation errors, never `500`;
  - dividend payer endpoints distinguish `data unavailable/provider limited` from true zero dividend history;
  - insider endpoints distinguish unsupported provider data from a genuine empty insider-trade history;
  - ratio/financial history exposes coverage status when only partial annuals, no quarters, or flat backfilled values are available;
  - Berkshire class B valuation avoids strong positive model classifications from one potentially inapplicable/scaled Graham-only result.
- Acceptance checklist:
  - Beta tester reports are linked from validation evidence and each high-severity issue is closed or explicitly deferred with owner and reason.
  - Investor role cannot trigger raw admin `403` experiences from primary navigation.
  - Compliance-sensitive labels avoid buy/sell/recommendation language on decision-support surfaces.
  - Demo checklist, audit rationale visibility, and advisor acknowledgement flows can be exercised end to end.
  - Mobile portfolio, screener, and KO review pass smoke checks for no global horizontal overflow, one primary main landmark, and no unexpected chart warnings.

---

## Group K — GCP Distribution & Operational Readiness

Goal: distribute the platform on Google Cloud without changing its decision-support domain behaviour. The API remains stateless; PostgreSQL and Redis remain the system of record/cache; scheduled work must not be duplicated as the API scales.

### Phase K1: Stakeholder Cloud Deployment
- Add a production container image for the Spring Boot service and deploy the API/demo pages to Cloud Run.
- Provision a non-production Cloud SQL for PostgreSQL instance and Memorystore for Redis; run Flyway on deployment and verify `/actuator/health` against both managed services.
- Inject FMP, JWT, and SMTP configuration from Secret Manager; no secret values in images, Terraform state, source control, or logs.
- Configure a minimum IAM footprint, HTTPS Cloud Run URL, structured logs, basic uptime/health alerting, and documented manual deployment/rollback steps.
- Keep this environment explicitly internal/stakeholder-only; it is not a commercial production release.

### Phase K2: Production-Shaped GCP Platform
- Define GCP infrastructure with Terraform: projects/environments, Artifact Registry, Cloud Run, Cloud SQL, Memorystore, Secret Manager references, service accounts, IAM, networking, Scheduler, Monitoring, and DNS/HTTPS resources.
- Split web traffic from background execution: Cloud Run serves the API; Cloud Run Jobs execute ingestion, quote refresh, dividend/insider updates, and alert detection; Cloud Scheduler triggers each job.
- Disable in-process `@Scheduled` execution in horizontally scalable API instances. Preserve job idempotency and `JobRunLog` observability.
- Add CI/CD that builds, tests, publishes an immutable image, plans infrastructure, deploys by environment, applies Flyway safely, and supports rollback.
- Use private connectivity to Cloud SQL and Memorystore, automated backups/PITR, a custom HTTPS domain, Cloud Monitoring dashboards/alerts, and documented recovery procedures.

### Phase K3: Commercial & Compliance Hardening
- Confirm and document FMP data-display/redistribution rights before customer-facing release; preserve the rule that raw provider data is not exposed publicly.
- Complete GDPR data mapping, retention/deletion policy, region/data-residency choice, access-control review, and applicable processor agreements.
- Add security hardening: least-privilege service accounts, secret rotation, audit logging, rate/edge protection, vulnerability/dependency scanning, and tested incident runbooks.
- Perform backup restoration and failure exercises for database, Redis/cache degradation, FMP outage fallback, Cloud Run Job retries, and email-delivery failures.
- Establish release approval evidence for MiFID II disclaimers, privacy, security, availability, monitoring, and operational ownership.

---

## Milestone Summary

| Milestone | Phases | Data Source | Deliverable |
|---|---|---|---|
| **M0: Demo** | Z1–Z5 | Yahoo Finance (free, primary) | Single-page "analyze a ticker" app — valuation + MoS, no auth, no DB |
| M1: Backend Running | A1, A2, A3 | — | Auth-protected API, full DB schema, Docker Compose |
| M2: Data Flowing | B1, B2, B3 | FMP primary / Yahoo fallback | Market data client + Redis cache + nightly ingestion jobs |
| **ML: Local Stack Demo** | LS1, LS2 | — | Login + protected endpoint via browser; H2 in-memory DB + Docker Redis; admin/admin default user |
| M3: Valuation Working | C1, C2, C3 | FMP primary / Yahoo fallback | DCF/Graham/DDM via API, persisted to DB |
| **M3.5: Connected Demo** | Val1, Val2 | FMP primary / Yahoo fallback | Authenticated single-stock analysis endpoint — real data → valuation → MoS, stakeholder-showable |
| **M3.6: Feature Demo UI** | FD1 | FMP primary / Yahoo fallback | Browser-based demo page exposing all features built through Val2: auth, health, seed, quick analysis, DCF, cache eviction, job trigger — no curl required |
| **M3.8: Pipeline Demo** | Score1, Score2 | FMP primary / Yahoo fallback | Seed → Valuate → Score → ranked table; full 5-factor Value Score validated end-to-end before screener |
| M4: Screener Live | D1, D2 | FMP primary / Yahoo fallback | Full screener API with Value Score, < 500ms |
| M5: Security Detail | E1, E2, E3 | FMP primary / Yahoo fallback | All per-stock endpoints live |
| M6: Portfolio | F1, F2, F3, F4 | FMP primary / Yahoo fallback | Watchlist + Portfolio Builder + Rebalancing |
| **M6.5: Full-Feature Demo** | PFD1 | FMP primary / Yahoo fallback | Single HTML page covering every endpoint through F4 — auth, health, screener, security detail, watchlist, portfolio, simulation, rebalancing |
| M7: Alerts | G1, G2 | FMP primary / Yahoo fallback | Automated alert detection + email delivery |
| M8: Frontend MVP | H1–H6, H4A, H4B, H8 | FMP primary / Yahoo fallback | Full React UI connected to backend, including shared-universe seeding, market-wide research, in-depth stock review page, and review-page portfolio-add integration |
| M8.5: Review Endpoint | H4C | FMP primary / Yahoo fallback | Dedicated backend review endpoint replacing frontend endpoint composition on the review page |
| **M8.8: Full Demo Assessment** | HD1, HD2, HD3, HD4 | FMP primary / Yahoo fallback | End-to-end demo walkthrough, UI/look-and-feel assessment, demo polish, beta persona reports, beta-driven feature implementation, and documented UX gaps before quality hardening |
| M9: Production Ready | H7, I1, I2 | FMP primary / Yahoo fallback | Dashboard + tests + observability |
| **M10: Google Sign-In (Backend)** | J1 | FMP primary / Yahoo fallback | Google OIDC backend — OAuth2 login, account linking, token handoff |
| **M11: Valuation Model Depth** | VM1, VM2 | FMP primary / Yahoo fallback | WACC calculator, DCF sensitivity matrix, EPV conservative floor, owner earnings, Graham criteria checklist, composite weight configurability |
| **M12: Scoring & Risk Intelligence** | SR1, SR2 | FMP primary / Yahoo fallback | MoS gate rule, sector-adaptive score weights, Piotroski F-Score, Altman Z-Score, cyclicality detection, earnings quality ratio |
| **M13: Moat & Business Quality** | MA1, MA2 | FMP primary / Yahoo fallback | ROIC consistency moat analysis, capital allocation tracking, historical valuation bands, long-term stability scoring |
| **M14: Google Sign-In (Completion)** | J2, J3 | FMP primary / Yahoo fallback | Google Sign-In UI, account lifecycle, security/integration/operational validation |
| **M15: Job Control** | JC1, JC2 | FMP primary / Yahoo fallback | Job run history, per-symbol ingestion events, runtime enable/disable, cron update, scoped partial ingestion triggers |
| **M16: Portfolio Intelligence** | PI1, PI2 | FMP primary / Yahoo fallback | Portfolio-level analytics, liquidity assessment, benchmark comparison, smart rebalancing with cost/urgency/tax awareness |
| **M17: Professional Workflow** | PW1, PW2 | FMP primary / Yahoo fallback | Research decision audit trail, investment checklist framework, valuation confidence scoring, data cross-verification, ADVISOR compliance scoping |
| **M18: Universe Curation** | SC1, SC2 | FMP primary / Yahoo fallback | Structured universe selection with filtering criteria, pre-built templates, preview-before-seed, and exclusion controls |
| **M19: Real Demo (Full Stack)** | RD1-1, RD1-2 | Yahoo Finance (free) | All features live with real Yahoo Finance data ingested on startup; Agent 1 full walkthrough with screenshots |
| **M20: Conservative Workflow Hardening** | L1-L4 | FMP primary / Yahoo fallback | Agent 1 prudent-value replay pack, 10-stock validation portfolio evidence, conservative review diagnostics, availability-state examples, and workflow enhancements |
| **M21: Real Demo (Curated)** | RD2-1 | Yahoo Finance (free) | Agent 1 validates curated universe workflow end to end with screenshots; comparison with manual-seed experience |
| **M22: Investor Replay Recycling** | RCL1, RCL2, RCL3, RCL4 | FMP primary / Yahoo fallback | Screener/API/symbol hardening, security-detail chart verification, beta-tester fix pack, and monitor-agent log correlation from investor-agent findings |
| **M23: GCP Stakeholder Deployment** | K1 | FMP primary / Yahoo fallback | Internal/stakeholder Cloud Run deployment backed by managed PostgreSQL and Redis |
| **M24: Production-Shaped GCP Platform** | K2 | FMP primary / Yahoo fallback | Terraform-managed, repeatable GCP environments with independently scheduled Cloud Run Jobs |
| **M25: Commercial Readiness** | K3 | FMP primary / Yahoo fallback | Compliance, security, resilience, and operational release evidence for customer-facing use |

> **M0 is self-contained.** It can be shown to stakeholders immediately, before any database schema or auth work begins. Z3 (Valuation Engine) is also the foundation for C1/C2 in the production path — no rework needed.
>
> **M3.5 is the first production-quality stakeholder demo.** It uses real FMP data, real auth, and the real DB — making it the natural checkpoint before the screener complexity begins.
>
> **M3.6 makes M3.5 browser-accessible.** A single HTML file — no curl, no scripting knowledge — exposes every feature built through Val2. Stakeholders can log in, seed data, inspect valuations, and trigger jobs from a web browser. It intentionally precedes Group H (full React UI) to keep early feedback cycles fast without a build step.
>
> **M3.8 validates the full pipeline.** Seed → Valuate → Score → Rank in a single call. Proves the ValueScore formula works end-to-end and gives stakeholders a ranked view before any screener UI exists. `ValueScoreService` introduced here is the same class D1 persists and exposes — no rework at merge.
>
> **M6.5 is the complete HTML test harness.** A single `full-demo.html` covers every backend endpoint built through Group F — screener, security detail, watchlist, portfolio, simulation, and rebalancing — in addition to all FD1 panels. Stakeholders and developers can exercise the full system from a browser before the React frontend (Group H) is started, and it remains available as a low-friction regression test page throughout H development.
>
> **M8.8 is the product-demo quality gate.** After the React frontend MVP is assembled, the full demo is walked like a stakeholder would use it: not just endpoint correctness, but visual consistency, page flow, copy, accessibility, responsive behavior, and obvious trust-eroding rough edges. After polish, beta-tester personas exercise the product from distinct investor mindsets and produce portfolio/watchlist reports with improvement recommendations; the best findings are selected, implemented, or explicitly deferred before Quality & Observability. It feeds fixes and explicit follow-up work into Quality & Observability rather than burying UX debt.
>
> **M10 lands the Google OIDC backend.** The identity plumbing is done; the UI and test hardening (J2/J3) are deferred until after the analytical engine is trustworthy — identity polish doesn't improve investment decisions.
>
> **M11 makes the valuation engine defensible.** A DCF without a transparent WACC is a toy. M11 adds computed WACC with auditable inputs, a sensitivity matrix that shows how fragile the estimate is, EPV as a conservative zero-growth floor, Graham's original multi-criteria checklist, and configurable composite weights. After M11, every fair value has traceable assumptions.
>
> **M12 makes scoring trustworthy.** An overvalued stock scoring 55/100 is a trust-destroying bug. M12 caps scores for negative-MoS stocks, adapts weights by sector so non-dividend payers aren't penalized, adds Piotroski F-Score and Altman Z-Score for fundamental strength and distress detection, flags cyclicality so users don't mistake peak earnings for fair value, and surfaces earnings quality. After M12, the screener ranking is safe to act on.
>
> **M13 addresses the heart of value investing.** Cheap is not the same as good. M13 adds ROIC consistency analysis (the strongest moat signal), capital allocation tracking (buybacks vs. dilution, insider ownership), historical valuation bands (is this P/E historically cheap or expensive for this stock?), and long-term stability scoring. Without this, the platform finds cheap stocks but can't tell the user whether they're worth owning.
>
> **M14 finishes Google Sign-In.** Now that the analytical engine is solid (VM, SR, MA done), the Google identity UI and test suite are completed.
>
> **M16 makes portfolio construction real-world-ready.** Analyzing stocks individually is necessary but insufficient. M16 adds weighted-average portfolio metrics, sector and holding concentration maps, liquidity assessment (can you actually exit this position?), benchmark comparison (is this portfolio genuinely different from the market?), and rebalancing intelligence with cost estimates, urgency ranking, and holding-period awareness.
>
> **M17 enables professional use.** Every investment decision should be traceable: what did the platform show, what assumptions were active, and why did the user act? M17 adds an immutable research audit trail, a user-defined investment checklist framework, intrinsic value confidence scoring, data cross-verification flags, and ADVISOR role compliance scoping.
>
> **M19 is the first real-data full-stack demo — now with trustworthy analytics.** By this point the valuation engine has WACC transparency, the scoring engine has the MoS gate and risk indicators, and moat analysis is in place. The demo proves a complete, analytically sound product rather than a polished shell.
>
> **M21 validates the curated workflow.** Agent 1 repeats the full demo but starts from universe curation instead of manual seeding. The comparison with M19 demonstrates that structured selection produces a more coherent research experience.
>
> **M22 recycles investor-agent findings before cloud deployment.** The autonomous investor replay surfaced issues that are small individually but trust-eroding in aggregate: screener empty-state contradiction, API threshold validation fragility, duplicate landmarks, and `BRK.B`/`BRK-B` symbol mismatch. M22 turns those findings into a focused hardening loop with monitor-agent log correlation before K1 exposes the demo to stakeholders in the cloud.
