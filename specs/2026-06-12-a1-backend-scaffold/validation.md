# Validation — Phase A1: Backend Project Scaffold

Phase A1 is complete and mergeable when all of the following pass.

---

## 1. Application Starts on `local` Profile

```
cd backend
docker compose -f ../docker-compose.yml up -d
mvn spring-boot:run -Dspring.profiles.active=local
```

**Pass criteria:**
- No startup errors in the Spring Boot log
- Flyway log line: `Successfully applied 1 migration to schema "public" (execution time ...)`
- Log shows `Tomcat started on port 8080`

---

## 2. Actuator Health Returns UP

```
curl http://localhost:8080/actuator/health
```

**Pass criteria:**
- HTTP 200
- `{"status":"UP","components":{"db":{"status":"UP"},...}}`
- Both `db` and (optionally) `redis` components show `UP`

---

## 3. Existing Tests Still Pass

```
mvn test
```

**Pass criteria:**
- All Z-phase unit tests pass (GrahamCalculator, DcfCalculator, etc.)
- No compilation errors from new dependencies

---

## 4. Demo Profile Unaffected

```
mvn spring-boot:run -Dspring.profiles.active=demo
curl http://localhost:8080/demo/analyze/AAPL
```

**Pass criteria:**
- Demo endpoint still returns a valid JSON response
- Flyway does NOT run under `demo` profile (check log — no Flyway output)

---

## 5. `.env.example` Is Complete

Manual check: every environment variable referenced in any `application-*.yml` file has a corresponding entry in `.env.example`.

---

## Out of Scope for A1

The following are NOT required to pass for merge:
- Auth endpoints (A3)
- Any DB tables beyond the Flyway baseline (A2)
- Redis cache hit/miss (B2)
- FMP client (B1)
