# Validation — Z1: Backend Scaffold (Minimal)

The implementation is complete and ready to merge when **all** of the following pass.

---

## 1. Build succeeds cleanly

```bash
./mvnw clean package -q
```

Expected: exits 0, produces `target/vis-backend-0.1.0-SNAPSHOT.jar`, zero compilation errors or warnings.

---

## 2. App boots on the demo profile with no external dependencies running

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Expected:
- No `BeanCreationException` or `DataSourceProperties` errors (no DB configured in demo)
- Log line: `Started VisApplication in X.XXX seconds`
- App listens on port 8080

---

## 3. Health endpoint returns UP

```bash
curl -s http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP"}
```

---

## 4. No unintended endpoints exposed

```bash
curl -s http://localhost:8080/actuator
```

Expected: only `health` link visible (not `env`, `beans`, `loggers`, etc.) — per demo profile config.

---

## 5. Test runner executes without failure

```bash
./mvnw test
```

Expected: exits 0. No tests exist yet, but the Surefire runner must complete cleanly (`Tests run: 0` is acceptable; runner failure is not).

---

## 6. Docker Compose services start

```bash
docker compose up -d
docker compose ps
```

Expected: `postgres` and `redis` containers reach `running` state. (The Spring Boot app is **not** in compose — this just validates the file is syntactically correct and the images pull.)

```bash
docker compose down
```

---

## 7. File checklist

Before merging, confirm each of these exists and is non-empty:

- [ ] `pom.xml` — correct `groupId`, `artifactId`, `version`, Java 21, all required dependencies
- [ ] `mvnw` + `mvnw.cmd` — executable Maven wrapper
- [ ] `src/main/java/it/mazzoni/vis/VisApplication.java` — `@SpringBootApplication` + `main()`
- [ ] `src/main/resources/application.yml` — default profile = `demo`
- [ ] `src/main/resources/application-demo.yml` — Caffeine cache, health-only actuator
- [ ] `src/main/resources/application-prod.yml` — placeholder with Redis cache config
- [ ] `docker-compose.yml` — PostgreSQL 16 + Redis 7
- [ ] `.env.example` — all env vars documented
- [ ] `.gitignore` — covers Maven, IDE, OS, `.env`

---

## 8. What does NOT need to be true at this point

- No database connectivity required
- No Redis connectivity required
- No authentication or JWT
- No business logic or domain classes
- No frontend
- 0 unit tests is acceptable (runner passes with empty suite)

---

## Merge criteria

All checks 1–7 green → open PR from `feature/z1-backend-scaffold` → `main`.
