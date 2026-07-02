# RD1-1 Plan - Yahoo Finance Startup Ingestion Profile

1. Profile and configuration
   - Add a `realDemo` Spring profile that uses Yahoo Finance as the market data source.
   - Keep all application features active while disabling cron-driven background jobs by default.
   - Configure local PostgreSQL and Redis through environment variables for Docker Compose compatibility.

2. Startup ingestion orchestration
   - Add a `realDemo` startup runner that reads `REAL_DEMO_TICKERS`, defaults to `AAPL,MSFT,KO,JNJ,PG,PEP,WMT,BRK-B,UNP,XOM`, and calls the existing seed pipeline.
   - After seeding, run one quote refresh, dividend update, and alert detection pass using the existing job services.
   - Persist observable job run and ingestion event records for the startup sequence.

3. Demo users
   - Reuse the local demo account seeding pattern and create an admin user plus one investor test user for the `realDemo` profile.
   - Keep credentials local-demo-only and documented, not production defaults.

4. Runtime packaging and documentation
   - Add `docker-compose.realDemo.yml` with PostgreSQL, Redis, backend, and frontend services.
   - Add `scripts/real-demo-guide.md` with startup steps, expected timing, accounts, and Yahoo Finance limitations.

5. Validation
   - Add focused backend tests for ticker parsing/defaults, user seeding, and startup runner orchestration.
   - Run `./mvnw.cmd test` from `backend`.
   - Run `npm run build` from `frontend`.
