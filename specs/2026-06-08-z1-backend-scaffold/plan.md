# Plan — Z1: Backend Scaffold (Minimal)

Each task group is independently committable. Complete them in order.

---

## 1. Maven Project Skeleton

**Goal:** A compilable Maven project with the correct coordinates.

- Create `pom.xml` with:
  - `groupId`: `it.mazzoni.vis`
  - `artifactId`: `vis-backend`
  - `version`: `0.1.0-SNAPSHOT`
  - Parent: `spring-boot-starter-parent` 3.x (latest stable)
  - Java 21 source/target
  - Dependencies:
    - `spring-boot-starter-web`
    - `spring-boot-starter-actuator`
    - `spring-boot-starter-cache`
    - `com.github.ben-manes.caffeine:caffeine` (in-memory cache for demo profile)
    - `spring-boot-starter-test` (scope test)
- Generate Maven Wrapper: `mvnw` + `mvnw.cmd` + `.mvn/wrapper/`
- Create `src/main/java/it/mazzoni/vis/VisApplication.java` with `@SpringBootApplication` + `main()`
- Verify: `./mvnw clean package -q` exits 0

---

## 2. Application Configuration

**Goal:** Profile-aware configuration with a self-contained `demo` profile.

- `src/main/resources/application.yml`:
  ```yaml
  spring:
    application:
      name: vis-backend
    profiles:
      active: demo   # overridden by env var SPRING_PROFILES_ACTIVE in prod
  ```

- `src/main/resources/application-demo.yml`:
  ```yaml
  spring:
    cache:
      type: caffeine
      caffeine:
        spec: maximumSize=500,expireAfterWrite=900s   # 15 min default
  management:
    endpoints:
      web:
        exposure:
          include: health
    endpoint:
      health:
        show-details: never
  server:
    port: 8080
  logging:
    level:
      it.mazzoni.vis: DEBUG
  ```

- `src/main/resources/application-prod.yml`:
  ```yaml
  # Populated in Phase A1 (full backend scaffold)
  spring:
    cache:
      type: redis
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,prometheus
  ```

---

## 3. Docker Compose

**Goal:** One-command local environment for phases that need PostgreSQL + Redis.

- Create `docker-compose.yml` at project root:
  ```yaml
  services:
    postgres:
      image: postgres:16-alpine
      environment:
        POSTGRES_DB: vis
        POSTGRES_USER: vis
        POSTGRES_PASSWORD: vis
      ports:
        - "5432:5432"
      volumes:
        - postgres_data:/var/lib/postgresql/data

    redis:
      image: redis:7-alpine
      ports:
        - "6379:6379"

  volumes:
    postgres_data:
  ```

- Note: `vis-backend` is intentionally **not** in this compose file — it is run directly by the developer via `./mvnw spring-boot:run`.

---

## 4. Environment & Project Housekeeping

**Goal:** Document environment variables and clean up VCS noise.

- Create `.env.example`:
  ```
  # Active Spring profile: demo | prod
  SPRING_PROFILES_ACTIVE=demo

  # Data source selection (used from Phase Z2 onward)
  # Values: yahoo | fmp
  MARKET_DATA_SOURCE=yahoo

  # FMP API key (production only, not needed for demo)
  FMP_API_KEY=

  # Database (not needed for demo profile)
  DATABASE_URL=jdbc:postgresql://localhost:5432/vis
  DATABASE_USERNAME=vis
  DATABASE_PASSWORD=vis

  # Redis (not needed for demo profile)
  REDIS_HOST=localhost
  REDIS_PORT=6379

  # JWT (not needed for demo profile — added in Phase A3)
  JWT_PRIVATE_KEY=
  JWT_PUBLIC_KEY=
  ```

- Create / verify `.gitignore`:
  ```
  # Maven
  target/
  !.mvn/wrapper/maven-wrapper.jar

  # IDE
  .idea/
  *.iml
  .vscode/
  .classpath
  .project
  .settings/

  # Environment
  .env

  # OS
  .DS_Store
  Thumbs.db
  ```

---

## 5. Smoke Test & Commit

**Goal:** Confirm the scaffold works end to end before moving to Z2.

- Run `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`
- Verify `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`
- Run `./mvnw test` → 0 failures (no tests yet, but runner must execute cleanly)
- Commit all files on branch `feature/z1-backend-scaffold`
