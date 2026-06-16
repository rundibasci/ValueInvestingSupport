# Requirements — Phase LS2: HTML Demo Client

## Scope

Deliver a single-page browser demo that proves the full auth + protected-endpoint stack is working.
A non-technical stakeholder can open `http://localhost:8080/demo.html`, log in as `admin/admin`,
click a button, and see a live JWT round-trip with cache header — without any build step.

## Decisions

| # | Decision | Reason |
|---|---|---|
| D1 | `demo.html` served as Spring Boot static resource | No separate server, no build step; works immediately when the app starts |
| D2 | Pure HTML + vanilla JS + fetch API | Zero dependencies; validates the backend, not a frontend stack |
| D3 | JWT stored in JS memory (not localStorage/cookie) | Simpler for a demo; avoids XSS/CSRF complexity not in scope for LS |
| D4 | `AdminController` with `GET /api/v1/admin/ping` created in this phase | Endpoint not yet present; LS2 is its natural home |
| D5 | On startup, Spring Boot prints the demo URL to the console | Makes the demo easy to find; implemented via `ApplicationReadyEvent` listener |
| D6 | App keeps running (no `--exit` flag, no short-lived runner) | Demo must stay up for a browser session |

## Context

- Predecessor: LS1 (`phase/ls1-local-stack-demo`) established the H2 demo profile, BCrypt-seeded admin user,
  and Docker Redis. LS2 builds directly on that profile.
- The `demo` Spring profile is active: H2 in-memory DB, Redis at `localhost:6379`, `DemoDataSeeder` seeds
  `admin/admin` on startup.
- Run command: `./mvnw spring-boot:run -Dspring-boot.run.profiles=demo` (or via IDE with profile `demo`).
- Port: `8080` (default; no override needed).

## Out of Scope

- Auth token refresh (refresh token endpoint exists but the demo page does not use it)
- Any other endpoints beyond `/api/v1/admin/ping`
- Automated test — validation is manual browser test (see `validation.md`)
- HTTPS / production hardening
