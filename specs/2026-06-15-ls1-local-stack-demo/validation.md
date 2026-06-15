# Validation — Phase LS1: H2 Demo Profile & Admin Seed

## Definition of Done

LS1 is complete and ready to merge when all checks below pass.

---

## 1. Flyway Runs Clean on H2

Start the application with the demo profile (no PostgreSQL, no Docker PostgreSQL service):

```bash
docker compose -f docker-compose.demo.yml up -d   # Redis only
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

Expected: application starts, Flyway logs show all migrations applied with no errors,
H2 console accessible at `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:demodb`).

---

## 2. Health Smoke Test

```bash
curl -s http://localhost:8080/actuator/health | jq .
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

Both `db` (H2) and `redis` components must report `UP`.

---

## 3. Integration Test Passes

`LocalStackDemoIT` must pass in CI. The test covers the full auth flow:

| Step | Assertion |
|---|---|
| `GET /actuator/health` | HTTP 200, `status: UP`, `db` and `redis` components UP |
| `POST /auth/login` with `admin@localstack.local`/`admin` | HTTP 200, `accessToken` present in response |
| `GET /api/v1/admin/ping` with Bearer token | HTTP 200, `{ "status": "ok", "role": "ADMIN" }` |

Run:

```bash
docker compose -f docker-compose.demo.yml up -d
mvn verify -Dgroups=integration
```

The `@ActiveProfiles("localstack")` annotation on `LocalStackDemoIT` sets the profile automatically.
All three assertions must be green.

---

## 4. DemoDataSeeder Is Idempotent

Restart the application twice with `spring.profiles.active=localstack`. After each restart confirm:

```sql
-- In H2 console
SELECT count(*) FROM users WHERE username = 'admin';
-- Must return 1, not 2, after two starts
```

---

## 5. Pre-existing Tests Unaffected

```bash
mvn test
```

All pre-existing unit and integration tests (B1, B2, A3 auth tests) must remain green.
The demo profile must not activate during normal `mvn test` runs (no `spring.profiles.active=localstack`
in `application-test.yml`).

---

## 6. PostgreSQL Path Unaffected

With the standard `local` profile (`docker compose up -d`), Flyway continues to use the
PostgreSQL migration scripts in `db/migration/` (not `db/migration/h2/`). Confirm by
starting the app normally:

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=local
# Flyway log must NOT show h2/ scripts being applied
```

---

## Merge Checklist

- [ ] `mvn spring-boot:run -Dspring-boot.run.profiles=demo` starts without Flyway errors
- [ ] `/actuator/health` returns `UP` with both `db` and `redis` components
- [ ] `LocalStackDemoIT` all three assertions green
- [ ] Restarting the demo app twice leaves exactly one `admin` user in H2
- [ ] `mvn test` (no demo profile) is fully green — no pre-existing tests broken
- [ ] PostgreSQL path (`local` profile) unaffected — Flyway uses `db/migration/` not `db/migration/h2/`
- [ ] PR description includes `actuator/health` response JSON as evidence
