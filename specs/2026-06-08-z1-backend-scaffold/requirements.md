# Requirements — Z1: Backend Scaffold (Minimal)

**Phase:** Z1 (Group Z — Demo Milestone)  
**Branch:** `feature/z1-backend-scaffold`  
**Date:** 2026-06-08  

---

## Scope

Create the Spring Boot skeleton that all subsequent phases build on. This phase produces a bootable application with a health endpoint and correct profile configuration — nothing more. No business logic, no database access, no authentication.

### In scope

- Spring Boot 3.x Maven project with Java 21
- Base package `it.mazzoni.vis`
- Spring profiles: `demo` (active by default locally), `prod` (placeholder)
- Spring Boot Actuator with `/actuator/health`
- `docker-compose.yml` for PostgreSQL 16 + Redis 7 (needed by later phases; container definitions only, not required to be running for Z1 validation)
- `.env.example` documenting all future environment variables
- `.gitignore` covering Java / Maven / IDE artefacts

### Out of scope

- Database schema or Flyway migrations (Phase A2)
- Yahoo Finance HTTP client (Phase Z2)
- Authentication / Spring Security (Phase A3)
- Any business logic or domain entities
- Redis or DB connectivity (not configured in `demo` profile)
- Frontend

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Build tool | Maven (Maven Wrapper `mvnw`) | Chosen by user; standard for Spring Boot teams |
| Java version | 21 | LTS, records, pattern matching, virtual threads (per tech-stack.md) |
| Base package | `it.mazzoni.vis` | User preference |
| Artifact ID | `vis-backend` | Short, descriptive |
| Architecture | Layered (controller → service → repository) | User choice; fastest for demo, sufficient for MVP |
| Demo profile | In-memory Caffeine cache, no DB datasource | Zero external dependencies to run locally |
| Actuator exposure | Only `health` exposed on `demo` profile | Security hygiene; other endpoints added per profile later |

---

## Package Structure

```
it.mazzoni.vis/
├── VisApplication.java          ← @SpringBootApplication entry point
├── config/                      ← Spring @Configuration classes
├── controller/                  ← @RestController (REST layer)
├── service/                     ← @Service (business logic)
├── repository/                  ← @Repository / Spring Data interfaces
├── domain/                      ← Domain entities and value objects
└── client/                      ← External API clients (Yahoo, FMP — added in Z2/B1)
```

---

## Context

- This is the **first code phase** of the project. It establishes conventions (package name, profile names, Maven coordinates) that all later phases inherit — changing them later is costly.
- The `demo` profile must be self-contained: the app must start with `--spring.profiles.active=demo` on a machine with no running PostgreSQL or Redis.
- Per `mission.md` principle #5 (cache-first): the cache abstraction (`spring-cache`) is wired in from the start, even if the demo profile uses Caffeine rather than Redis.
- Per `tech-stack.md`, the `MARKET_DATA_SOURCE` env var will select the data client implementation in later phases; its existence is documented in `.env.example` here.
