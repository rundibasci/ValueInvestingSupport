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

### Changed
- `application-prod.yml` extended with datasource, JPA `validate` DDL, and Flyway configuration using environment variable references
- `.env.example` updated to list `local` as a valid `SPRING_PROFILES_ACTIVE` value alongside `demo` and `prod`

### Fixed
- `DemoAnalysisControllerTest`: added `@Import(SecurityConfig.class)` to restore all 4 passing tests after Spring Security was added to the classpath
