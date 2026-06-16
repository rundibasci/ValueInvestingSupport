# Roadmap — Value Investing Advisory Platform

Implementation broken into small, shippable phases. Each phase produces working, tested code that the next phase builds upon. Phases within a group can overlap but should be completed in listed order within the group.

**Data source strategy:**
- **Demo milestone (M0):** Yahoo Finance public API — zero cost, no API key, sufficient for a single-stock vertical slice.
- **Production milestone (M1+):** FMP Premium — official, bulk, full screener. Switch is isolated to the data client layer; Valuation Engine and Score Engine are untouched.

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
  - `YahooMarketDataClient` — reuses Z2 Yahoo Finance client; active when `MARKET_DATA_SOURCE=yahoo`
  - `FmpMarketDataClient` — FMP WebClient with API key, retry (exponential backoff, max 3), error handling (429/503); active when `MARKET_DATA_SOURCE=fmp`
- Spring `@Profile` or `@ConditionalOnProperty` to select implementation at startup
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
- Response structure mirrors the M0 schema; adds `dataAsOf` (snapshot date) and `"source": "fmp"`
- Error cases: 404 if ticker not in DB, 422 if snapshot older than 7 days (stale data guard)
- RULE-06 guard respected: DCF omitted if < 3 years of positive FCF; falls back to Graham-only composite
- MiFID II disclaimer required in response body
- Unit test: `QuickAnalysisControllerTest` (MockMvc, mocked service)

### Phase Val2: Demo Seed Endpoint & Smoke Test
- `POST /api/v1/admin/seed` (ADMIN only) — triggers on-demand FMP ingestion for a configurable ticker list (`SEED_TICKERS` env var, default `AAPL,MSFT,KO,JNJ`)
- For each ticker: fetch company profile → fundamentals → ratios → price quote → run `ValuationService` → persist
- Response: array of `{ symbol, compositeFairValue, marginOfSafety, recommendation }` per seeded ticker
- Integration test `ValuationDemoIT`: login as admin → `POST /api/v1/admin/seed?tickers=AAPL` → `GET /api/v1/securities/AAPL/quick-analysis` → assert `marginOfSafety` non-null and `recommendation` set
- `scripts/demo.sh`: documented curl sequence (login → seed → analyze → logout) — shareable with stakeholders

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
- Performance target: < 500ms for 5000+ rows with typical filters

---

## Group E — Security Detail API

### Phase E1: Company Profile & Financials
- `GET /api/v1/securities/search?q=` — autocomplete (name + symbol)
- `GET /api/v1/securities/{symbol}` — full profile (company info + latest snapshot)
- `GET /api/v1/securities/{symbol}/financials` — 10y annual + 8 quarters + TTM
- `GET /api/v1/securities/{symbol}/ratios` — 10y ratio history

### Phase E2: Dividends, Insiders & Growth
- `GET /api/v1/securities/{symbol}/dividends` — full dividend history + streak + growth CAGR
- `GET /api/v1/securities/{symbol}/insiders` — recent insider transactions
- `GET /api/v1/securities/{symbol}/growth` — CAGR at 3y, 5y, 10y for revenue, FCF, EPS
- `GET /api/v1/securities/{symbol}/peers` — peer comparison table

### Phase E3: Full Valuation on Security Detail
- `GET /api/v1/securities/{symbol}/valuation` — all models (DCF, Graham, DDM, FMP DCF)
- Includes Fair Value composite, MoS, scenario range
- Analyst estimates + price target consensus aggregated

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
- React Router v6 routes: `/`, `/screener`, `/securities/:symbol`, `/portfolio`, `/watchlist`
- TanStack Query client setup + auth token interceptor
- Layout: sidebar nav + main content area + header

### Phase H2: Authentication UI
- Login page → POST `/auth/login`
- Token storage (memory + httpOnly cookie for refresh)
- Protected route wrapper

### Phase H3: Screener UI
- Filter panel (all screener filters with range sliders + dropdowns)
- Results table: sortable columns, pagination
- Preset selector (Graham / Dividend / Quality)
- Click row → navigate to Security Detail

### Phase H4: Security Detail UI
- Overview tab: company profile, sector, country, market cap, management
- Financials tab: 10y revenue/income/FCF bar charts (Recharts)
- Ratios tab: PE, ROIC, ROE, debt trend line charts
- Valuation tab: DCF custom form, Fair Value vs price, MoS gauge
- Dividends tab: dividend history bar chart, streak, payout ratio
- Growth tab: CAGR table at 3/5/10y
- Insider tab: recent trades table
- Add to Watchlist button

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

## Milestone Summary

| Milestone | Phases | Data Source | Deliverable |
|---|---|---|---|
| **M0: Demo** | Z1–Z5 | Yahoo Finance (free) | Single-page "analyze a ticker" app — valuation + MoS, no auth, no DB |
| M1: Backend Running | A1, A2, A3 | — | Auth-protected API, full DB schema, Docker Compose |
| M2: Data Flowing | B1, B2, B3 | Yahoo → FMP switchable | Market data client + Redis cache + nightly ingestion jobs |
| **ML: Local Stack Demo** | LS1, LS2 | — | Login + protected endpoint via browser; H2 in-memory DB + Docker Redis; admin/admin default user |
| M3: Valuation Working | C1, C2, C3 | either | DCF/Graham/DDM via API, persisted to DB |
| **M3.5: Connected Demo** | Val1, Val2 | FMP | Authenticated single-stock analysis endpoint — real FMP data → valuation → MoS, stakeholder-showable |
| **M3.8: Pipeline Demo** | Score1, Score2 | FMP | Seed → Valuate → Score → ranked table; full 5-factor Value Score validated end-to-end before screener |
| M4: Screener Live | D1, D2 | FMP (bulk) | Full screener API with Value Score, < 500ms |
| M5: Security Detail | E1, E2, E3 | FMP | All per-stock endpoints live |
| M6: Portfolio | F1, F2, F3, F4 | FMP | Watchlist + Portfolio Builder + Rebalancing |
| M7: Alerts | G1, G2 | FMP | Automated alert detection + email delivery |
| M8: Frontend MVP | H1–H6 | FMP | Full React UI connected to backend |
| M9: Production Ready | H7, I1, I2 | FMP | Dashboard + tests + observability |

> **M0 is self-contained.** It can be shown to stakeholders immediately, before any database schema or auth work begins. Z3 (Valuation Engine) is also the foundation for C1/C2 in the production path — no rework needed.
>
> **M3.5 is the first production-quality stakeholder demo.** It uses real FMP data, real auth, and the real DB — making it the natural checkpoint before the screener complexity begins.
>
> **M3.8 validates the full pipeline.** Seed → Valuate → Score → Rank in a single call. Proves the ValueScore formula works end-to-end and gives stakeholders a ranked view before any screener UI exists. `ValueScoreService` introduced here is the same class D1 persists and exposes — no rework at merge.
