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
- Presents a single focused research packet for one stock, optimized for reading and comparison rather than tab navigation.
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
- Actions: add to watchlist, add to portfolio, open custom DCF controls, refresh/seed symbol if allowed, and return to Screener/Security Detail.
- Includes empty, loading, partial-data, stale-data, unavailable, and error states.
- Acceptance checklist:
  - `/securities/:symbol/review` renders as a standalone page, not a modal or hidden tab.
  - The page displays DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, and unavailable-data labels.
  - The page includes historical graphs for earnings, debt, ROI/ROIC, and ROE.
  - The page displays source coverage and freshness for FMP/Yahoo Finance/Mixed data.
  - The page can be opened from Screener, Security Detail, Watchlist, Portfolio, and Seed results.
  - Non-admin investors can open the page for seeded symbols.

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

## Group I — Quality & Observability

### Phase I1: Test Coverage
- Unit tests: all Calculator classes (DCF, Graham, DDM, ValueScore)
- Integration tests: Screener API, Valuation endpoint, Auth flow
- Test DB: H2 or Testcontainers PostgreSQL

### Phase I2: Observability
- Prometheus metrics via Micrometer: FMP API call count/latency, cache hit rate, screener latency
- Structured JSON logging (Logback)
- FMP quota monitoring metric (calls consumed vs plan limit)
- Spring Boot Actuator endpoints exposed to internal network only

---

## Group J — Google Account Sign-In

Goal: let a user sign in with a Google account while keeping the platform's existing local JWT, role, ownership, and refresh-token model unchanged. Google is used only to prove identity through OpenID Connect; the platform remains the authorization authority.

### Phase J1: Google OpenID Connect Backend
- Add Spring Security OAuth2 Client support for Google Authorization Code + OpenID Connect login; use `openid`, `email`, and `profile` scopes only.
- Expose `GET /oauth2/authorization/google` as the login entry point and configure the callback at `/login/oauth2/code/google`; make callback and authorization paths public while all `/api/**` routes retain their existing JWT protection.
- Register Google client configuration exclusively through environment variables: `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and an allow-listed application redirect URI. Add placeholders and setup guidance to `.env.example`; never commit a client secret.
- Validate the ID token issuer, audience/client ID, signature, expiry, nonce/state, and `email_verified=true`. Use Google's immutable `sub` claim as the provider identity; do not treat a mutable display name as an identifier.
- Persist an OAuth identity (`provider=GOOGLE`, `providerSubject`) linked to the existing `User` record, with uniqueness enforced on `(provider, providerSubject)` and normalized unique user email. Add a Flyway migration and repository support.
- On first successful sign-in, create an `INVESTOR` user only when the verified email is not already registered. For an existing password user with the same verified email, link the Google identity to that user without creating a duplicate. Google sign-in must never grant or change `ADMIN` or `ADVISOR` roles.
- After successful Google authentication, issue the same RS256 access and refresh tokens used by `POST /auth/login`, then redirect to the configured frontend callback without exposing tokens in query parameters; use the existing httpOnly refresh-cookie pattern and a short-lived, single-use handoff mechanism for the access token.
- Preserve existing username/password login, refresh, logout, JWT revocation, and demo-profile behavior.

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

## Group K - GCP Distribution & Operational Readiness

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
| M8: Frontend MVP | H1–H6, H4A, H8 | FMP primary / Yahoo fallback | Full React UI connected to backend, including shared-universe seeding, market-wide research, and a dedicated in-depth stock review page |
| M9: Production Ready | H7, I1, I2 | FMP primary / Yahoo fallback | Dashboard + tests + observability |
| **M10: Google Sign-In** | J1, J2, J3 | FMP primary / Yahoo fallback | Google OIDC sign-in issuing the existing platform JWTs, with safe account linking and validated callbacks |
| **M11: GCP Stakeholder Deployment** | K1 | FMP primary / Yahoo fallback | Internal/stakeholder Cloud Run deployment backed by managed PostgreSQL and Redis |
| **M12: Production-Shaped GCP Platform** | K2 | FMP primary / Yahoo fallback | Terraform-managed, repeatable GCP environments with independently scheduled Cloud Run Jobs |
| **M13: Commercial Readiness** | K3 | FMP primary / Yahoo fallback | Compliance, security, resilience, and operational release evidence for customer-facing use |

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
> **M10 adds identity, not a second authorization system.** Google OpenID Connect verifies the person; the application maps that identity to its own user, roles, ownership rules, RS256 access tokens, and refresh-token lifecycle. This keeps every existing protected API and portfolio boundary consistent regardless of how the user signed in.
