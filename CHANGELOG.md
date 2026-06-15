# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com) · Versioning: [SemVer](https://semver.org)

## [Unreleased]

### Added
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

### Fixed
- `DemoAnalysisControllerTest`: added `@Import(SecurityConfig.class)` to restore all 4 passing tests after Spring Security was added to the classpath
- `FmpMarketDataClientTest`: added `spring.cache.type=none` to prevent cross-test cache pollution introduced by `@Cacheable` on `FmpMarketDataClient`
