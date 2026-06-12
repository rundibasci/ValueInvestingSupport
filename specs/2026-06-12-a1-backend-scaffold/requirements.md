# Requirements — Phase A1: Backend Project Scaffold

## Scope

Extend the existing Z1 Spring Boot project (`backend/`) to be production-architecture-ready: add the full dependency set, Spring profiles for `local` / `test` / `prod`, Flyway with the first empty migration, and a `.env.example` covering all environment variables the system will eventually need.

**Not in scope for A1:**
- DB schema tables (Phase A2)
- Auth implementation (Phase A3)
- Redis cache annotations (Phase B2)
- Any FMP client work (Phase B1)

The `demo` profile and all Z-phase code remain unchanged.

---

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Build tool | Maven | Already in use from Z1; no migration needed |
| Extend vs rewrite | Extend Z1 `backend/` project | Avoids duplication; demo profile stays intact |
| Spring Security in A1 | Stub only — permit all in `local`/`demo` | Full JWT auth is A3; stub prevents autoconfiguration failures when security is on classpath |
| Flyway dialect | PostgreSQL | Target DB per tech-stack |
| ORM | Spring Data JPA + Hibernate 6 | Per tech-stack; Java records used for domain types |
| Cache store | Spring Data Redis (Lettuce) | Required for B2; wiring it in now avoids profile complexity later |
| `test` profile DB | H2 in-memory | Fast unit/integration tests without Docker; Testcontainers reserved for Group I |

---

## Context

The `docker-compose.yml` (root) already runs PostgreSQL 16 + Redis 7 (added in Z1). The `application.yml` default profile is `demo`. A1 adds `local` and `test` as named Spring profiles so the production path can be developed and tested without touching the demo profile.

All new dependencies must be declared in `backend/pom.xml`. The `local` profile connects to the Docker Compose services on `localhost`. The `prod` profile reads all sensitive values from environment variables — no hardcoded credentials anywhere.

The `.env.example` at the repo root lists every variable the system will need across all phases. A1 only needs the DB/Redis/Spring ones; FMP and JWT keys are included as placeholders.

---

## Files Changed / Created

| Path | Action | Notes |
|---|---|---|
| `backend/pom.xml` | Edit | Add JPA, Flyway, PG driver, Redis, Security |
| `backend/src/main/resources/application.yml` | Edit | Default profile stays `demo`; add Flyway enabled flag |
| `backend/src/main/resources/application-local.yml` | Create | DB URL, Redis, actuator full exposure |
| `backend/src/main/resources/application-test.yml` | Create | H2 in-memory, Flyway enabled, Redis disabled |
| `backend/src/main/resources/db/migration/V1__init.sql` | Create | Empty placeholder (comment only) |
| `backend/src/main/java/.../config/SecurityConfig.java` | Create | Permit-all stub; disables CSRF for stateless demo |
| `.env.example` | Edit | Add DB, Redis, JWT, FMP placeholders |
