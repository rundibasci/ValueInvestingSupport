# RD1-1 Validation - Yahoo Finance Startup Ingestion Profile

## Acceptance Checks

- `realDemo` profile starts with `market-data.source=yahoo`.
- `REAL_DEMO_TICKERS` controls startup seed scope and defaults to the 10-symbol roadmap list.
- Startup invokes seed, quote refresh, dividend update, and alert detection once.
- Startup records job run state and per-symbol ingestion events visible through existing job-control APIs.
- Admin and investor demo accounts exist after startup.
- `docker-compose.realDemo.yml` starts PostgreSQL, Redis, backend, and frontend.
- `scripts/real-demo-guide.md` documents startup, accounts, expected timing, and Yahoo Finance limitations.

## Test Strategy

- Unit or slice tests cover real-demo ticker parsing and startup runner orchestration without live Yahoo calls.
- User seeding test verifies admin and investor accounts are created idempotently.
- Existing backend tests continue to pass.
- Existing frontend build continues to pass.

## Validation Commands

- `cd backend && .\mvnw.cmd test`
- `cd frontend && npm run build`

## Manual QA

- Run `docker compose -f docker-compose.realDemo.yml up --build`.
- Confirm backend health at `http://localhost:8080/actuator/health`.
- Log in as admin and investor using the documented demo credentials.
- Confirm job control shows the real-demo startup run and ingestion events.

## Known Risks

- Live Yahoo Finance responses may be rate-limited, incomplete, or structurally different from fixtures.
- Full startup duration depends on network latency and provider availability.
- Some symbols may seed partial data while still allowing the rest of the demo to continue.
