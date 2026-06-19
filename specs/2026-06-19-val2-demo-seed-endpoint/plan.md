# Plan — Val2: Demo Seed Endpoint & Smoke Test

## Task Group 1: Seed Service

1.1 Create `SeedService` in `backend/src/main/java/.../seed/`:
  - Method: `List<SeedResult> seedTickers(List<String> symbols)`
  - For each symbol (sequential or parallel with configurable concurrency):
    - Call `MarketDataClient.getProfile` → upsert `Security`
    - Call `MarketDataClient.getFundamentals` → persist `FundamentalSnapshot`
    - Call `MarketDataClient.getRatios` → persist `RatioSnapshot`
    - Call `MarketDataClient.getQuote` → persist `PriceQuote`, warm Redis
    - Call `ValuationService.calculate()` → persist `ValuationResult`
    - Catch per-ticker exceptions → wrap in `SeedResult` with `error` field

1.2 Create `SeedResult` record: `symbol`, `companyName`, `compositeFairValue`, `marginOfSafety`, `recommendation`, `error` (nullable)

## Task Group 2: Seed Controller & Request Wiring

2.1 Create `SeedController` (`POST /api/v1/admin/seed`):
  - Resolve ticker list: `tickers` query param → `SEED_TICKERS` env var → hardcoded default `AAPL,MSFT,KO,JNJ`
  - Delegate to `SeedService`; return `List<SeedResult>` as JSON

2.2 Add `SEED_TICKERS` to `application.yml` (bound via `@Value`) and `.env.example`

2.3 Confirm Spring Security permits `ADMIN` role only on `/api/v1/admin/**` (should already be set from Val1/LS phases; verify, don't duplicate)

## Task Group 3: Unit Test

3.1 `SeedServiceTest` (mocked `MarketDataClient` + mocked `ValuationService`):
  - Happy path: two tickers → both results populated
  - One ticker FMP 404 → error result in array, other ticker succeeds
  - FMP 503 → propagated exception (controller returns 503)

3.2 `SeedControllerTest` (MockMvc, mocked `SeedService`):
  - Happy path: assert 200 + array shape
  - INVESTOR role → assert 403

## Task Group 4: Integration Test (ValuationDemoIT)

4.1 Create `ValuationDemoIT` annotated `@ActiveProfiles({"test", "fmpkey"})` + `@SpringBootTest(webEnvironment = RANDOM_PORT)`:
  - Setup: Testcontainers PostgreSQL (or use `@Sql` reset); Redis via Docker or Testcontainers
  - Step 1: `POST /auth/login` (admin credentials) → extract JWT
  - Step 2: `POST /api/v1/admin/seed?tickers=AAPL` → assert 200, `marginOfSafety` non-null, `recommendation` non-blank
  - Step 3: `GET /api/v1/securities/AAPL/quick-analysis` → assert 200, `marginOfSafety` matches seed response (within rounding), `dataAsOf` is today's date, `source` = "fmp"

4.2 Add `backend/src/test/resources/application-fmpkey.yml` to `.gitignore` verification (already in `**/application-fmpkey.yml` glob — confirm, don't add duplicate)

## Task Group 5: Demo Script

5.1 Create `scripts/demo.sh`:
  - Header: usage comment, `BASE_URL` arg defaulting to `http://localhost:8080`
  - Sections: Login → Seed → Analyze → Logout, each labelled with `echo`
  - Capture JWT from login response (use `jq` or `grep -o`)
  - Exit non-zero if any curl step returns HTTP 4xx/5xx
  - `chmod +x` in the script itself via git tracking (`git update-index --chmod=+x`)

5.2 Add a one-line reference to `scripts/demo.sh` in the project README (or create `scripts/README.md` if none exists)

## Task Group 6: Env & Config Cleanup

6.1 Add `SEED_TICKERS=AAPL,MSFT,KO,JNJ` to `.env.example`

6.2 Verify `.gitignore` covers `**/application-fmpkey.yml` (no change needed if already present)
