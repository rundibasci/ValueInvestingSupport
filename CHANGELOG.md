# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org)

## [Unreleased]

### Added
- H2: React authentication experience with a responsive “Research before conviction” login page, protected-route return navigation, memory-only access tokens, httpOnly refresh-cookie session restoration, logout, accessible expiry/error states, and an ADMIN-only user-provisioning screen. Backend auth now supports credentialed local Vite requests and a local-stack refresh-token fallback when Docker Redis is unavailable; feature specifications under `specs/2026-06-21-h2-authentication-ui/`.
- H1: Vite + React 18 + strict TypeScript + TailwindCSS frontend scaffold with TanStack Query, a configurable backend URL, token-aware API-client boundary, responsive sidebar/header shell, and routes for overview, screener, security detail, portfolio, and watchlist; feature specifications under `specs/2026-06-21-h1-frontend-scaffold/`.
- Group G2: Email-only alert delivery via configurable Spring Mail/SMTP for high-priority persisted alerts, with delivery-attempt state, safe failure handling, idempotent resend prevention, and no committed credentials; `PUT /api/v1/alerts/{id}/ack` now acknowledges only the owning user's alert. Includes Flyway V10, alert delivery/controller tests, and feature specifications under `specs/2026-06-21-g2-alert-delivery/`. Live provider/mailbox testing remains intentionally deferred to a later pre-production phase.
- Group G1: Scheduled alert-detection job evaluating all eight existing alert types from persisted watchlist, portfolio, price, valuation, score, dividend, insider, fundamental, and rebalance data; new alerts are user-scoped, `ACTIVE`, and deduplicated by user, symbol, type, and day. Includes a 5% price-movement rule, alert detection tests, and feature specifications under `specs/2026-06-21-g1-alert-detection/`.
- PFD1: standalone `full-demo.html` stakeholder page covering the full workflow through F4 — authentication and admin utilities, screening, security detail, watchlist management, portfolio and holding management, simulation, and rebalance proposals — with inline request feedback, raw JSON inspectors, MiFID II disclaimer, same-origin API routing, and local-stack demo credentials; feature specifications under `specs/2026-06-21-pfd1-full-feature-demo/`.
- Group F4: Portfolio rebalancing API — save `PENDING` proposals from simulation-derived or explicit targets, retrieve them, and explicitly apply them to local holdings with captured-price trade lines, ownership checks, holding-fingerprint stale protection, duplicate-apply conflict protection, Flyway V9 persistence, and the mandatory MiFID II decision-support disclaimer; feature specs under `specs/2026-06-21-f4-portfolio-rebalancing/`.
- Group F2: Portfolio CRUD API — `GET /api/v1/portfolios` (list user portfolios), `POST /api/v1/portfolios` (create), `GET /api/v1/portfolios/{id}` (detail with holdings), `POST /api/v1/portfolios/{id}/holdings` (add holding), `PUT /api/v1/portfolios/{id}/holdings/{holdingId}` (update shares/cost), `DELETE /api/v1/portfolios/{id}/holdings/{holdingId}` (remove holding); `PortfolioService` with user-scoped ownership check and duplicate-symbol guard (409); `HoldingRepository` and `PortfolioRepository` extended with user-scoped queries; Flyway V8 — composite indexes `idx_portfolio_user`, `idx_holding_portfolio`; 8 DTOs; 14 unit tests (`PortfolioControllerTest`), 7-assertion `PortfolioIT` (Testcontainers PostgreSQL); spec files `specs/2026-06-20-f2-portfolio-crud/`
- Group F1: Watchlist API — `GET/POST/PUT/{id}/DELETE/{id} /api/v1/watchlist` per-user CRUD with MoS and fundamental-degrade alert thresholds; `GET /api/v1/watchlist/alerts` returns active alerts for the authenticated user; `WatchlistService` with email-based user resolution, auto-creation of default watchlist on first add, and duplicate-symbol guard (409); `WatchlistItemRepository` with three user-scoped derived query methods; Flyway V7 (`fundamental_degrade_threshold` column on `watchlist_item`, `idx_watchlist_user` index); 4 DTOs; 11 unit tests; `WatchlistIT` (Testcontainers PostgreSQL); spec files `specs/2026-06-20-f1-watchlist/`
- Group E (E1+E2+E3): 9 Security Detail endpoints — `GET /api/v1/securities/search?q=`, `GET /{symbol}` (profile with 7-day stale guard), `/{symbol}/financials` (annuals/quarters/TTM), `/{symbol}/ratios`, `/{symbol}/dividends` (streak + CAGR 3/5/10y), `/{symbol}/insiders` (trailing 12 months), `/{symbol}/growth` (revenue/FCF/EPS CAGR), `/{symbol}/peers` (same-sector top-5 by market-cap proximity), `/{symbol}/valuation` (DCF scenarios, Graham, DDM, composite, MoS, analyst consensus, MiFID II disclaimer); `DividendsService` and `GrowthService`; `AnalystEstimate` entity + `AnalystEstimateRepository`; Flyway V5 (5 composite indexes) + V6 (`analyst_estimate` table); 19 DTOs in `it.mazzoni.vis.security.dto`; 9 unit-test classes (30 tests, all passing); `SecurityDetailIT` (Testcontainers PostgreSQL, 11 assertions); spec files `specs/2026-06-20-e1-e2-e3-security-detail/`
- Group D — D1: `GET /api/v1/securities/{symbol}/score` — returns the latest `ValueScore` for a symbol, computing on demand if none is stored; D2: `POST /api/v1/screener` — paginated stock screener with sector, exchange, MoS, score, ROIC, D/E, and dividend-yield filters sorted by any score field across pages via EntityManager + CriteriaBuilder theta-joins with correlated MAX subqueries; `GET /presets` (Graham, Dividend, Quality), `GET /sectors`, `GET /exchanges` helpers; Flyway V4 — 5 composite indexes; 19 tests (MockMvc unit, `@DataJpaTest`/H2, Testcontainers PostgreSQL IT with 5 000-row seed and < 500 ms gate); feature specification `specs/2026-06-20-d1-d2-screener-and-scoring/`
- Phase Score1/2: `POST /api/v1/admin/pipeline-run` (ADMIN only) — runs the full Seed → Valuate → Score → Rank pipeline for a configurable ticker list; returns results sorted by `totalScore DESC`; per-ticker errors surfaced as inline rows without aborting the batch
- Phase Score1/2: `ValueScoreService.compute(symbol) → ValueScore` — 5-factor formula: MoS (30 pts), Quality/ROIC (25 pts), Safety/D-E (20 pts), Growth/revenue (15 pts), Dividend (10 pts); ROIC falls back to ROE when absent; persists `ValueScore` to DB; same class D1 screener will import
- Phase Score1/2: `PipelineRunService` — orchestrates `SeedService` then `ValueScoreService` per ticker; sorts results by `totalScore DESC` with null scores last
- Phase Score1/2: `scripts/pipeline-demo.sh` — end-to-end shell demo: login → `POST /admin/pipeline-run` → ranked table printed via jq
- Phase Score1/2: 8 tests — `ValueScoreServiceTest` (6 unit tests: all sub-scores, ROIC→ROE fallback, currentRatio cap, dividend streak, null-safe paths), `PipelineControllerTest` (MockMvc), `PipelineDemoIT` (integration, `@Tag("integration")`)
- Phase Score1/2: Feature specification (`specs/2026-06-20-score-pipeline-demo/`) — plan, requirements, validation
- Phase Val2: `POST /api/v1/admin/seed` — admin endpoint that fetches live market data for a ticker list (default `SEED_TICKERS`: AAPL, MSFT, KO, JNJ), upserts `Security`, `FundamentalSnapshot` (one row per FCF year), `RatioSnapshot`, `PriceQuote`, runs valuation, returns `SeedResult[]`; active on all profiles except `demo`
- Phase Val2: `SeedService` — orchestrates per-symbol upsert in a single `@Transactional`; captures `MarketDataException` per ticker and returns `SeedResult.failed` without aborting the batch
- Phase Val2: `SeedResult` record — per-symbol outcome with `symbol`, `companyName`, `compositeFairValue`, `marginOfSafety`, `recommendation`, and `error` fields
- Phase Val2: `LocalDataSeeder` `@Component` (`@Profile("local")`) — seeds `admin@example.com / Admin1234!` on `ApplicationReadyEvent` if absent; serves the `local` Spring profile
- Phase Val2: `scripts/demo.sh` — end-to-end shell demo: login → `POST /admin/seed` for each ticker → `GET /securities/{symbol}/quick-analysis`; parameterised via env vars
- Phase Val2: 3 test classes — `SeedControllerTest` (MockMvc), `SeedServiceTest` (unit), `ValuationDemoIT` (full integration seed → quick-analysis flow)
- Phase Val2: Feature specification (`specs/2026-06-19-val2-demo-seed-endpoint/`) — plan, requirements, validation
- Phase Val1: `GET /api/v1/securities/{symbol}/quick-analysis` — authenticated endpoint (all roles) returning composite fair value, MoS, recommendation, and MiFID II disclaimer from DB-backed fundamentals; 422 when snapshot older than 7 days, 404 on unknown symbol
- Phase Val1: `QuickAnalysisService` — 7-day stale guard, delegates to `ValuationService` with conservative default params from `ValuationDefaultsProperties`; maps result to `QuickAnalysisResponse` with `dataAsOf` and `source: "fmp"` fields
- Phase Val1: `ValuationDefaultsProperties` `@ConfigurationProperties("valuation.defaults")` — WACC 9%, growth Y1–5 8%, Y6–10 4%, terminal 2.5%; DDM intentionally absent (skipped in quick-analysis)
- Phase Val1: `StaleDataException` — 422 thrown when `FundamentalSnapshot.reportDate` older than 7 days
- Phase Val1: 5-test `QuickAnalysisIT` — happy path, 404 unknown symbol, 422 stale snapshot, 401 no token, 422 all models ineligible
- Phase Val1: Feature specification (`specs/2026-06-18-val1-single-stock-analysis/`) — plan, requirements, validation
- Phase C3: `ValuationService.calculate(symbol, params) → ValuationOutcome` — orchestrates DCF, Graham, DDM; composite blended with configurable weights (DCF 60%, Graham 25%, DDM 15%) with proportional normalization when models are excluded
- Phase C3: `MarginOfSafetyCalculator` + `Recommendation` derivation (STRONG_BUY ≥ 25%, QUALITY_VALUE ≥ 10%, FAIR_VALUE ≥ 0%, OVERVALUED < 0%); `ValuationResult` persisted to DB on every call
- Phase C3: `POST /api/v1/securities/{symbol}/valuation/dcf` — authenticated endpoint; response includes `weights` map for transparency; `ValuationWeightsProperties` configurable per environment
- Phase C3: 19-test suite — `ValuationServiceTest` (all weight-normalization paths), `ValuationControllerTest` (MockMvc), reference-value weight assertion
- Phase C3: Feature specification (`specs/2026-06-16-c3-composite-fair-value/`) — plan, requirements, validation
- Phase C2: `DcfCalculator.calculate(DcfInput) → Optional<DcfResult>` — explicit Y1–10 projections + Gordon Growth terminal; RULE-06 guard returns `Optional.empty()` when `fcfYearsPositive < 3`
- Phase C2: `DcfResult` record — `fairValue`, `fairValueLow` (WACC+2%), `fairValueHigh` (WACC−1%), `enterpriseValue`, parameter snapshot
- Phase C2: 16-test `DcfCalculatorTest` — reference calculation, RULE-06 guard, WACC ± scenario bounds
- Phase C2: Feature specification (`specs/2026-06-16-c2-dcf-engine/`) — plan, requirements, validation
- Phase C1: `GrahamCalculator.calculate(eps, bvps)` — √(22.17 × EPS × BVPS); `GrahamNotApplicableException` when EPS ≤ 0 or BVPS ≤ 0
- Phase C1: `DdmCalculator.calculate(dpsTtm, growthRate, requiredReturn, consecutiveYears)` — Gordon Growth DDM; `DdmNotEligibleException` (RULE-07: < 5 consecutive dividend years), `DdmNotApplicableException` (requiredReturn ≤ growthRate)
- Phase C1: 18-test suite — `GrahamCalculatorTest` and `DdmCalculatorTest` with known reference values
- Phase C1: Feature specification (`specs/2026-06-16-c1-graham-ddm/`) — plan, requirements, validation
- Phase B3: Flyway migration V3 — `job_run_log` table (UUID PK, `RUNNING / SUCCESS / FAILED` status, records-processed counter, error message); H2-compatible variant under `db/migration/h2/`; `source` column added to `valuation_result` with `idx_valuation_source` index
- Phase B3: `JobRunLog` JPA entity + `JobRunLogRepository` (`findTop1ByJobNameOrderByStartedAtDesc`)
- Phase B3: `JobLogWriter` (package-private `@Component`, `REQUIRES_NEW` transaction per operation) + `JobRunLogger` `@Service` — two-class pattern that avoids Spring AOP self-invocation on `@Transactional`
- Phase B3: 7 ingestion jobs — `BulkProfileSyncJob`, `BulkFundamentalsSyncJob`, `BulkRatiosSyncJob`, `BulkDcfSyncJob`, `QuoteRefreshJob`, `DividendUpdateJob`, `InsiderTradingJob`; each job is idempotent (existence-check before insert) and appends a `JobRunLog` row on every run
- Phase B3: `@EnableScheduling` + `ThreadPoolTaskScheduler` (pool = 4, prefix `ingestion-`); `JobsProperties` `@ConfigurationProperties` record binding `app.jobs.*` (enabled flag, exchange list, per-job cron map)
- Phase B3: `IngestionJobHealthIndicator` — Spring Boot Actuator `HealthIndicator` named `ingestionJobs` checking 7 jobs against staleness windows (quote: 20 min, insider: 90 min, all nightly jobs: 26 h); reports `DOWN` on FAILED status or overdue last run
- Phase B3: `POST /api/v1/admin/jobs/{jobName}/run` — async job trigger (202 response) via `CompletableFuture.runAsync`
- Phase B3: `MarketDataClient` interface extended with `listSymbols`, `getDividendHistory`, `getInsiderTransactions`, `getFmpDcf`; FMP implementations added for all four; Yahoo stubs throw `UnsupportedOperationException` for bulk methods
- Phase B3: FMP DTOs — `FmpStockListEntry`, `FmpDividendEntry`, `FmpDividendHistoryResponse`, `FmpInsiderTradingEntry`, `FmpDcfEntry`
- Phase B3: `scripts/ingestion-demo.sh` — manual smoke-test sequence: login → trigger bulk-profile-sync → health check → trigger quote-refresh
- Phase B3: Unit tests — `JobRunLoggerTest` (success and failure paths with `REQUIRES_NEW` isolation) and `IngestionJobHealthIndicatorTest` (all-UP, FAILED→DOWN, never-run→DOWN)
- Phase B3 feature specification: plan, requirements, and validation criteria (`specs/2026-06-16-b3-data-ingestion-jobs/`)
- Phase LS1: `application-localstack` Spring profile — H2 in-memory datasource, Flyway H2 dialect, Docker Redis, test-only RS256 key pair embedded in YAML
- Phase LS1: `docker-compose.demo.yml` — Redis-only Compose file for the Local Stack Demo (no PostgreSQL needed)
- Phase LS1: H2-compatible Flyway migrations under `db/migration/h2/` for V1 and V2 core schema
- Phase LS1: `DemoDataSeeder` `@Component` (`@Profile("localstack")`) — seeds `admin@localstack.local / admin` on `ApplicationReadyEvent` if absent
- Phase LS1: Feature specification (`specs/2026-06-15-ls1-local-stack-demo/`) — plan, requirements, validation
- Phase LS2: `demo.html` static resource — vanilla JS login form calls `POST /auth/login`, stores JWT in memory; Ping button calls `GET /api/v1/admin/ping` with Bearer token; response panel shows HTTP status, role, and `X-Cache` header
- Phase LS2: `DemoStartupListener` `@Component` (`@Profile("localstack")`) — prints demo URL and credentials to console on `ApplicationReadyEvent`; server stays running
- Phase LS2: `AdminPingController` — `GET /api/v1/admin/ping` (ADMIN role) returning `{ "status": "ok", "role": "ADMIN" }`
- Phase LS2: Feature specification (`specs/2026-06-16-ls2-html-demo-client/`) — plan, requirements, validation
- Secret management: `.env` (gitignored) for runtime FMP key and DB credentials; `backend/src/test/resources/application-fmpkey.yml` (gitignored via `**/application-fmpkey.yml`) activating the real FMP key in integration tests; `@ActiveProfiles({"test","fmpkey"})` convention established
- `FmpMarketDataClientLiveIT` — 8 live integration tests against the real FMP API (profile, quote, ratios, fundamentals, stock list, dividends, insider trades, DCF); extended endpoints gracefully accept NOT_FOUND / SERVICE_UNAVAILABLE to accommodate plan-level access differences
- Maven `integration-test` profile with `combine.self="override"` on surefire config to prevent `excludedGroups=integration` from merging; `**/*IT.java` added to surefire includes; run with `mvn test -Pintegration-test`

### Changed
- Yahoo Finance documented as explicit FMP fallback in mission statement and roadmap
- H2 Flyway migrations moved from `db/migration/h2/` to `db/migration-h2/` to decouple the H2 migration path from the PostgreSQL path
- `.gitignore`: added `.env` and `**/application-fmpkey.yml` patterns to prevent accidental credential commit
- `application.yml`: `app.jobs.*` block added (under single `app:` key) with cron schedules and exchange list; `application-test.yml` sets all crons to `"-"` to disable scheduled triggers during the test suite
- Repository interfaces extended with existence-check methods (`existsBy…`) and `@Query`-based `findAllDistinctSymbols()` on `WatchlistItemRepository` and `HoldingRepository` for ingestion idempotency and watchlist/portfolio symbol collection
- `specs/mission.md`: added design principle #7 — "Secrets never in source control"
- `specs/roadmap.md`: FMP API key local setup documented under data source strategy
- `specs/tech-stack.md`: new "Secrets & Local Configuration" section documenting `.env` (runtime) and `application-fmpkey.yml` (test) patterns with the never-commit rule

- Project constitution: mission statement, tech-stack decisions, and phased roadmap (`specs/`)
- Phase Z1 feature specification: requirements, implementation plan, and validation criteria
- Spring Boot 3.4.0 backend scaffold with `demo` profile (no database or Redis required)
- Maven 3.9.16 wrapper (`mvnw`) and base package `it.mazzoni.vis`
- Caffeine in-memory cache wired via Spring Cache for the demo profile
- `/actuator/health` endpoint (only endpoint exposed in demo profile)
- `docker-compose.yml` with PostgreSQL 16 and Redis 7 for future phases
- `.env.example` documenting all future environment variables
- Phase Z2: `YahooFinanceClient` (Spring WebClient, no API key) with `quoteSummary` + `chart` endpoints, response DTOs, and Caffeine 15-min cache
- Phase Z2: Yahoo Finance adapter mapping DTOs → domain `FundamentalSnapshot` and `RatioSnapshot` records
- Phase Z3: `GrahamCalculator`, `DcfCalculator` (pessimistic / base / optimistic scenarios), and `MarginOfSafetyCalculator`; RULE-06 guard skips DCF if fewer than 3 years of positive FCF
- Phase Z4: `GET /demo/analyze/{symbol}` — unauthenticated endpoint returning full valuation JSON (symbol, price, DCF range, Graham Number, composite fair value, MoS, recommendation); 404 on unknown symbol, 503 on Yahoo Finance outage
- Phase Z4: `GlobalExceptionHandler` mapping domain exceptions to structured JSON error responses
- Phase Z5: Single-page React demo UI (`demo-ui/`) — ticker input, valuation result display, MoS colour-coded badge (green > 15 %, yellow 5–15 %, red < 5 %), and MiFID II disclaimer footer; Yahoo Finance crumb auth handled transparently
- Phase A1: Production dependencies added to `pom.xml` — Spring Data JPA, Spring Data Redis, Spring Security, Flyway (core + PostgreSQL dialect), PostgreSQL JDBC driver (runtime), H2 (test scope), and `spring-security-test`
- Phase A1: Spring profiles `local` and `test` — `local` connects to Docker Compose PostgreSQL + Redis with Flyway enabled; `test` uses H2 in-memory with Redis auto-configuration excluded
- Phase A1: Flyway baseline migration `V1__init.sql`; Flyway disabled globally, enabled per `local` / `test` / `prod` profile
- Phase A1: `SecurityConfig` permit-all stub (CSRF disabled) as placeholder for JWT RS256 enforcement in Phase A3
- Phase A1 feature specification: requirements, implementation plan, and validation criteria (`specs/2026-06-12-a1-backend-scaffold/`)
- Phase A2: JPA entities for all domain objects — `Security`, `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote`, `ValuationResult`, `ValueScore`, `DividendRecord`, `InsiderTrade`, `User`, `Portfolio`, `Holding`, `Watchlist`, `WatchlistItem`, `Alert`
- Phase A2: Flyway migration `V2__core_schema.sql` with all tables, indexes, and FK constraints; `price_quote` partitioned by month
- Phase A2: Spring Data repositories for all 14 domain entities
- Phase A2 feature specification: requirements, implementation plan, and validation criteria (`specs/2026-06-12-a2-domain-entities/`)
- Phase A3: `POST /auth/login` → JWT RS256 access token (15 min) and refresh token (7 days) pair
- Phase A3: `POST /auth/refresh` → issues new access token from a valid refresh token
- Phase A3: `POST /auth/logout` → revokes refresh token
- Phase A3: Spring Security filter chain enforcing JWT on `/api/**`; `/auth/**` and `/actuator/health` remain public
- Phase A3: Admin-only `POST /api/v1/admin/users` for user provisioning; ADMIN role seeded on startup
- Phase A3 feature specification: requirements, implementation plan, and validation criteria (`specs/2026-06-14-a3-authentication/`)
- Phase B1: `MarketDataClient` interface (`getProfile`, `getFundamentals`, `getRatios`, `getQuote`) decoupling the valuation engine from any specific data source
- Phase B1: `YahooMarketDataClient` — implements `MarketDataClient` via the existing Yahoo Finance client; active when `MARKET_DATA_SOURCE=yahoo`
- Phase B1: `FmpMarketDataClient` — implements `MarketDataClient` via FMP REST API with exponential-backoff retry (3 attempts, handles 429 and 503); active when `MARKET_DATA_SOURCE=fmp`
- Phase B1: `@ConditionalOnProperty` selection of Yahoo or FMP implementation at startup; `FmpWebClientConfig` only created when `market-data.source=fmp`
- Phase B1 feature specification: requirements, implementation plan, and validation criteria (`specs/2026-06-14-b1-market-data-client/`)
- Phase B2: `@Cacheable` on all four `MarketDataClient` methods (both Yahoo and FMP implementations) using Spring Cache
- Phase B2: `RedisCacheManager` with per-cache TTLs — `mdc-quote` 15 min, `mdc-ratios` 6 h, `mdc-fundamentals` 6 h, `mdc-profile` 24 h; active when `spring.cache.type=redis`
- Phase B2: `CacheKeyHelper` — deterministic cache key strategy `mdc:{source}:{endpoint}:{SYMBOL}`
- Phase B2: `CacheEvictionService` + `DELETE /api/v1/admin/cache/{symbol}` — admin endpoint that evicts all four cache entries for a ticker in one call
- Phase B2: `CacheTtlProperties` bound from `app.cache.ttl.*` — TTLs configurable per environment without recompile
- Phase B2 feature specification: requirements, implementation plan, and validation criteria (`specs/2026-06-15-b2-redis-cache-layer/`)

### Changed
- `application-prod.yml` extended with datasource, JPA `validate` DDL, and Flyway configuration using environment variable references
- `.env.example` updated to list `local` as a valid `SPRING_PROFILES_ACTIVE` value alongside `demo` and `prod`
- Roadmap: Group Val (Connected Valuation Demo) inserted between Group C and Group D as a production-quality stakeholder checkpoint before screener complexity begins
- `application.yml`: added `market-data.source: yahoo` default and `app.cache.ttl.*` TTL defaults
- `application-local.yml`: added `spring.cache.type: redis` to activate `RedisCacheManager` for local dev
- `application-test.yml`: added `spring.cache.type: simple` and `market-data.source: fmp` to standardise the test context without requiring a live Redis instance
- Roadmap: Group Score Full Pipeline Demo (Score1, Score2) inserted between Group Val and Group D as a full-pipeline validation milestone before screener

### Fixed
- `DemoAnalysisControllerTest`: added `@Import(SecurityConfig.class)` to restore all 4 passing tests after Spring Security was added to the classpath
- `FmpMarketDataClientTest`: added `spring.cache.type=none` to prevent cross-test cache pollution introduced by `@Cacheable` on `FmpMarketDataClient`
- H2 schema validation on `localstack` profile: `CLOB` → `VARCHAR(32767)` for `description` columns in `security` and `portfolio` tables (V2) and `error_message` in `job_run_log` (V3); H2 2.x maps `CLOB` to `Types#CLOB` but JPA `String` fields without `@Lob` expect `Types#VARCHAR`
