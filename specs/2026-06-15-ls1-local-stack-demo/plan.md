# Plan — Phase LS1: H2 Demo Profile & Admin Seed

## Group 1 — H2 Dependency & Demo Profile

1. Add H2 dependency to `pom.xml` with `runtime` scope (no effect on `prod` profile):
   ```xml
   <dependency>
     <groupId>com.h2database</groupId>
     <artifactId>h2</artifactId>
     <scope>runtime</scope>
   </dependency>
   ```

2. Create `src/main/resources/application-localstack.yml`:
   - H2 in-memory datasource (`jdbc:h2:mem:demodb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;NON_KEYWORDS=VALUE`)
   - `spring.flyway.enabled=true`, `spring.flyway.locations=classpath:db/migration/h2`
   - Redis host/port from env vars with defaults `localhost:6379`; `spring.cache.type=redis`
   - `spring.jpa.hibernate.ddl-auto=validate` (Flyway owns the schema)
   - `spring.h2.console.enabled=true` (useful for debugging during dev; does not affect tests)
   - RSA test key pair embedded under `jwt.private-key` / `jwt.public-key` (test-only; safe to commit)

## Group 2 — Dual-Dialect Flyway Migrations

3. Create directory `src/main/resources/db/migration/h2/`.

4. For each existing migration file in `db/migration/` (`V1__init.sql`, `V2__core_schema.sql`, etc.),
   produce an H2-compatible copy in `db/migration/h2/` with the same version number and description:
   - Drop `CREATE EXTENSION` statements entirely
   - Replace `PARTITION BY RANGE (...)` block with a plain `CREATE TABLE` (no partitioning)
   - Replace `GENERATED ALWAYS AS IDENTITY` → `BIGINT GENERATED ALWAYS AS IDENTITY` if H2 supports it,
     otherwise `BIGINT AUTO_INCREMENT`
   - Replace `TIMESTAMPTZ` → `TIMESTAMP`
   - Replace `JSONB` → `CLOB`
   - Remove `CONCURRENTLY` from any `CREATE INDEX CONCURRENTLY` statements
   - Remove any `ALTER TABLE ... SET (autovacuum_*)` or PostgreSQL storage-parameter clauses

5. Run `mvn spring-boot:run -Dspring-boot.run.profiles=localstack` locally and confirm Flyway completes
   without errors before moving on.

## Group 3 — DemoDataSeeder

6. Create `it.mazzoni.vis.localstack.DemoDataSeeder`:
   ```java
   @Component
   @Profile("localstack")
   public class DemoDataSeeder {
       // inject UserRepository and PasswordEncoder
       // on ApplicationReadyEvent: if admin@localstack.local not present, insert ADMIN user
   }
   ```
   - Use `userRepository.existsByEmail("admin@localstack.local")` guard for idempotency.
   - Password encoded with `BCryptPasswordEncoder`.
   - Login credentials: email `admin@localstack.local`, password `admin`.

## Group 4 — Admin Ping Endpoint

7. Add `GET /api/v1/admin/ping` to `AdminController` (or create it if the class doesn't exist yet):
   - Secured with `@PreAuthorize("hasRole('ADMIN')")` (or via Spring Security filter chain config).
   - Reads the role from `SecurityContextHolder.getContext().getAuthentication()`.
   - Returns `ResponseEntity<Map<String, String>>` with `{ "status": "ok", "role": "ADMIN" }`.

## Group 5 — Docker Compose Demo File

8. Create `docker-compose.demo.yml` at the project root:
   ```yaml
   services:
     redis:
       image: redis:7-alpine
       ports:
         - "6379:6379"
   ```
   No PostgreSQL service — H2 replaces it for the demo profile.

## Group 6 — Integration Test

9. Create `src/test/java/.../LocalStackDemoIT.java`:
   - Annotate with `@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)`
   - Set `@TestPropertySource(properties = "spring.profiles.active=demo")`
   - Inject `TestRestTemplate`

10. Test method 1 — health check:
    ```
    GET /actuator/health
    → assert HTTP 200
    → assert body.status == "UP"
    → assert body.components contains "db" and "redis" (both UP)
    ```

11. Test method 2 — login → JWT → protected endpoint:
    ```
    POST /auth/login  body: { "username": "admin", "password": "admin" }
    → assert HTTP 200
    → extract accessToken from response body

    GET /api/v1/admin/ping  header: Authorization: Bearer <accessToken>
    → assert HTTP 200
    → assert body.status == "ok"
    → assert body.role == "ADMIN"
    ```

12. Prerequisite note in test class Javadoc: Redis must be running (`docker compose -f docker-compose.demo.yml up -d`) before this IT runs. Add a `@Tag("integration")` so it can be excluded from unit-only runs.
