# Plan — Phase A1: Backend Project Scaffold

Each task group is independently verifiable. Complete in order.

---

## 1. pom.xml — Add Production Dependencies

Add to `backend/pom.xml` (Spring Boot manages versions via parent BOM):

- `spring-boot-starter-data-jpa` — JPA + Hibernate 6
- `spring-boot-starter-data-redis` — Spring Data Redis (Lettuce client)
- `spring-boot-starter-security` — Security filter chain
- `org.flywaydb:flyway-core` — DB migration
- `org.flywaydb:flyway-database-postgresql` — Flyway PostgreSQL support (required for Flyway 10+)
- `org.postgresql:postgresql` — JDBC driver (runtime scope)
- `com.h2database:h2` — in-memory DB for test profile (test scope)

---

## 2. Spring Profile YMLs

### 2a. `application.yml` (edit)
- Keep `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:demo}`
- Add `spring.flyway.enabled: false` as the global default (enabled per profile)
- Add `spring.jpa.open-in-view: false`

### 2b. `application-local.yml` (create)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vis
    username: vis
    password: vis
  jpa:
    hibernate.ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
  data:
    redis:
      host: localhost
      port: 6379
management:
  endpoints:
    web.exposure.include: health,info
  endpoint:
    health.show-details: always
```

### 2c. `application-test.yml` (create)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:vis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate.ddl-auto: create-drop
  flyway:
    enabled: true
  autoconfigure:
    exclude: org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

### 2d. `application-prod.yml` (edit)
Update existing file to use env-var references:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
  jpa:
    hibernate.ddl-auto: validate
  flyway:
    enabled: true
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
```

---

## 3. Flyway — First Migration

Create `backend/src/main/resources/db/migration/V1__init.sql`:
```sql
-- Baseline migration. Schema created in Phase A2.
```

This file must exist for Flyway to start cleanly; it records the baseline version in `flyway_schema_history`.

---

## 4. Security Stub

Create `SecurityConfig.java` under the `config` package:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

This permits all requests. Phase A3 replaces this with JWT RS256 enforcement on `/api/**`.

---

## 5. `.env.example` Update

Update root `.env.example` to include all env vars the full system will use:

```
# Spring profile
SPRING_PROFILES_ACTIVE=local

# Database (required for local/prod profiles)
DATABASE_URL=jdbc:postgresql://localhost:5432/vis
DATABASE_USERNAME=vis
DATABASE_PASSWORD=vis

# Redis (required for local/prod profiles)
REDIS_HOST=localhost
REDIS_PORT=6379

# Market data source
MARKET_DATA_SOURCE=yahoo

# FMP (production only)
FMP_API_KEY=

# JWT (Phase A3 — not needed for demo or local without auth)
JWT_PRIVATE_KEY=
JWT_PUBLIC_KEY=
```

---

## 6. Smoke Test

After all changes:

1. Start Docker Compose: `docker compose up -d`
2. Run: `mvn spring-boot:run -Dspring.profiles.active=local` from `backend/`
3. Verify: `curl http://localhost:8080/actuator/health` returns `{"status":"UP",...}`
4. Verify Flyway log output: `Successfully applied 1 migration to schema "public"`
5. Run `mvn test` (demo profile, H2 test profile) — all existing tests pass
