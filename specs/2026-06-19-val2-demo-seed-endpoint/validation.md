# Validation — Val2: Demo Seed Endpoint & Smoke Test

## Definition of Done

All items below must pass before this phase is merged into main.

## 1. Unit Tests

- [ ] `SeedServiceTest` passes: happy path (2 tickers), per-ticker 404 error, FMP 503 propagation
- [ ] `SeedControllerTest` passes: 200 with correct array shape (admin), 403 for INVESTOR role
- [ ] `mvn test` (without `fmpkey` profile) exits 0

## 2. Integration Test (requires FMP key locally)

- [ ] `ValuationDemoIT` passes end-to-end:
  - Login → 200 + JWT received
  - `POST /api/v1/admin/seed?tickers=AAPL` → 200, response array contains `{ symbol: "AAPL", marginOfSafety: <non-null>, recommendation: <non-blank> }`
  - `GET /api/v1/securities/AAPL/quick-analysis` → 200, `marginOfSafety` non-null, `dataAsOf` = today, `source` = "fmp"
- [ ] Test class uses `@ActiveProfiles({"test", "fmpkey"})` — no FMP key committed

## 3. Demo Script

- [ ] `scripts/demo.sh` is executable (`git ls-files --stage scripts/demo.sh` shows mode `100755`)
- [ ] Script runs successfully against a local running instance: `./scripts/demo.sh http://localhost:8080`
  - Each step prints a labelled section header
  - JWT is captured and passed to subsequent calls
  - Final exit code is 0 when all steps succeed
- [ ] Script exits non-zero and prints an error when seed returns 5xx

## 4. Endpoint Correctness

- [ ] `POST /api/v1/admin/seed` (no `tickers` param) uses the `SEED_TICKERS` env var default
- [ ] `POST /api/v1/admin/seed?tickers=AAPL,KO` seeds exactly those two tickers
- [ ] Response array contains one entry per ticker; failed tickers include `"error"` field, others include `compositeFairValue`
- [ ] INVESTOR JWT → 403 Forbidden

## 5. Data Persistence

- [ ] After seed, `SELECT * FROM fundamental_snapshot WHERE symbol='AAPL'` returns a row with today's date
- [ ] `ValuationResult` row exists for AAPL with `composite_fair_value` non-null
- [ ] `PriceQuote` row exists for AAPL with today's date

## 6. Config & Secrets

- [ ] `.env.example` contains `SEED_TICKERS=AAPL,MSFT,KO,JNJ`
- [ ] `application-fmpkey.yml` does not appear in `git status` or `git log` (gitignored)
- [ ] No API key value appears in any committed file (`git grep -i "fmp_api_key" -- '*.yml' '*.properties' '*.java'` returns nothing with a key value)

## 7. Merge Criteria

- All unit tests pass in CI (no FMP key in CI)
- Integration test is documented as requiring local `application-fmpkey.yml` and is excluded from CI pipeline (tagged or in a separate Maven profile)
- `scripts/demo.sh` committed with execute permission
- No regressions in Val1 (`QuickAnalysisControllerTest` still passes)
