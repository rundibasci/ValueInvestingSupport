# Requirements — Phase LS1: H2 Demo Profile & Admin Seed

## Context

Phases B1 (Market Data Client) and B2 (Redis Cache Layer) are complete. LS1 validates that
the auth stack, JPA schema, and Redis cache are all wired correctly before Group C (Valuation
Engine) begins. It does this by running the application against H2 in-memory database and a
Docker-managed Redis instance — no PostgreSQL container needed.

The goal is a runnable, verifiable slice: start the app with `spring.profiles.active=localstack`,
confirm `/actuator/health` reports UP, then exercise the full login → JWT → protected endpoint
flow with an integration test.

**Profile name:** `localstack` (not `demo`). The existing `demo` profile is reserved for the
Z-phase Yahoo Finance demo and disables auth entirely (`DemoSecurityConfig` permits all,
`AuthController` and `JwtService` are `@Profile("!demo")`). Using `demo` would make JWT auth
impossible. The `localstack` profile activates all `@Profile("!demo")` beans and adds H2 +
Flyway on top.

LS2 (HTML demo client) is out of scope for this phase.

## Scope

### In scope

- `application-localstack.yml`: H2 datasource, Flyway locations pointing to H2-compatible scripts,
  Redis at `localhost:6379`, JPA DDL validation only (Flyway creates the schema), RSA test keys embedded
- H2 runtime dependency in `pom.xml`
- Dual-dialect Flyway migration files: H2-compatible copies of every existing migration in
  `src/main/resources/db/migration/h2/`; PostgreSQL-incompatible syntax handled per migration file
- `DemoDataSeeder` `@Component` (active on `localstack` profile only): on
  `ApplicationReadyEvent`, inserts `User { email=admin@localstack.local, password=BCrypt("admin"), role=ADMIN }`
  if not already present — idempotent
- `GET /api/v1/admin/ping` endpoint (ADMIN role required) returning
  `{ "status": "ok", "role": "ADMIN" }` — needed by the integration test
- `docker-compose.demo.yml`: Redis service only (no PostgreSQL)
- Full integration test `LocalStackDemoIT` (see Validation)

### Out of scope

- LS2 HTML demo client
- Changes to FMP or Yahoo data clients
- Changes to the Valuation Engine or Score Engine
- Cache metrics or Prometheus integration
- Any new Flyway migrations beyond making existing ones H2-compatible

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | H2 in-memory datasource (`jdbc:h2:mem:demodb`) via `application-demo.yml` | Zero-dependency demo; no PostgreSQL container needed |
| D2 | Strict dual-dialect Flyway: separate H2 migration copies in `db/migration/h2/` | H2-incompatible DDL (partitioning, extensions, identity syntax) must be adapted — comments or best-effort wrapping are insufficient for a schema that must actually run |
| D3 | `spring.flyway.locations` in `application-demo.yml` overrides the default to `classpath:db/migration/h2` | Cleanly separates H2 and PostgreSQL scripts; no conditional logic inside migration files |
| D4 | `DemoDataSeeder` uses `@Profile("localstack")` + idempotency guard | Prevents duplicate admin inserts on restart; only active in localstack context |
| D5 | `GET /api/v1/admin/ping` returns role claim from Security context, not hardcoded | Proves the JWT was parsed and the ADMIN role was correctly extracted by the filter chain |
| D6 | Integration test uses `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `TestRestTemplate` | Tests the full HTTP stack including Spring Security filter chain; MockMvc would bypass the real servlet container |

## H2 Compatibility Notes

Syntax that must be adapted in H2 migration copies:

| PostgreSQL syntax | H2 equivalent |
|---|---|
| `PARTITION BY RANGE (...)` — declarative partitioning | Plain table (no partitioning) |
| `CREATE EXTENSION IF NOT EXISTS ...` | Omit entirely |
| `GENERATED ALWAYS AS IDENTITY` | `IDENTITY` or `AUTO_INCREMENT` |
| `TIMESTAMPTZ` | `TIMESTAMP` |
| `JSONB` | `VARCHAR` or `CLOB` |
| `CREATE INDEX CONCURRENTLY` | `CREATE INDEX` (no `CONCURRENTLY`) |

## Environment Variables

No new variables. The demo profile hardcodes safe local defaults:

```yaml
# application-localstack.yml
spring:
  datasource:
    url: jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  flyway:
    enabled: true
    locations: classpath:db/migration/h2
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
jwt:
  private-key: |   # test-only RSA key — safe to commit
    -----BEGIN PRIVATE KEY-----
    ...
    -----END PRIVATE KEY-----
  public-key: |
    -----BEGIN PUBLIC KEY-----
    ...
    -----END PUBLIC KEY-----
```

Redis host/port still respect `REDIS_HOST`/`REDIS_PORT` env vars so the profile works in CI.
