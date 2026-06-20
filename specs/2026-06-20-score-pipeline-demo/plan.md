# Plan — Group Score: Pipeline Demo (M3.8)

## Task Group 1: ValueScoreService (full 5-factor formula)

1.1 Create `ValueScoreService` in `it.mazzoni.vis.scoring` package
  - Method: `compute(String symbol) → ValueScore`
  - Loads latest `FundamentalSnapshot`, `RatioSnapshot`, and `ValuationResult` from DB for the symbol
  - Calculates all 5 sub-scores per formulas in `requirements.md`
  - Sets `scoreDate = LocalDate.now()`, associates to the `Security` entity
  - Persists via `ValueScoreRepository` and returns the saved entity

1.2 Unit test: `ValueScoreServiceTest`
  - Mock repositories; supply known inputs (AAPL-like: MoS=22%, ROIC=18%, D/E=0.8, revenue growth=8%, yield=0.6%)
  - Assert each sub-score value matches expected: mosScore=20, qualityScore=25, safetyScore=14, growthScore=10, dividendScore=0, totalScore=69
  - Assert null-field path: if `roic` is null and `roe` is non-null, quality score still computed via ROE fallback

---

## Task Group 2: Pipeline Run Endpoint (Score1)

2.1 Create request/response records in `it.mazzoni.vis.pipeline.dto`:
  - `PipelineRunRequest`: `List<String> tickers`
  - `PipelineRunResult`: `String symbol`, `String companyName`, `BigDecimal compositeFairValue`, `BigDecimal marginOfSafety`, `BigDecimal totalScore`, `String recommendation`

2.2 Create `PipelineRunService` in `it.mazzoni.vis.pipeline`:
  - Method: `run(List<String> tickers) → List<PipelineRunResult>`
  - For each ticker (sequential, not parallel — to avoid FMP rate limits):
    1. Fetch company profile → persist/update `Security`
    2. Fetch fundamentals → persist `FundamentalSnapshot`
    3. Fetch ratios → persist `RatioSnapshot`
    4. Fetch quote → persist `PriceQuote`
    5. Call `ValuationService.calculate(symbol, defaultParams)` → persist `ValuationResult`
    6. Call `ValueScoreService.compute(symbol)` → persist `ValueScore`
    7. Map to `PipelineRunResult`
  - Errors per ticker are caught and logged; that ticker gets an error row (symbol, null scores, recommendation=`"ERROR: <message>"`)
  - Return list sorted by `totalScore DESC` (null scores sort last)

2.3 Create `PipelineController` in `it.mazzoni.vis.pipeline`:
  - `POST /api/v1/admin/pipeline-run`
  - Security: `hasRole("ADMIN")`
  - Request body: `PipelineRunRequest`
  - Delegates to `PipelineRunService.run()`
  - Returns `200 OK` with `List<PipelineRunResult>`

2.4 Unit test: `PipelineControllerTest`
  - MockMvc, mocked `PipelineRunService`
  - Assert 200 + correct JSON shape for a 2-ticker request
  - Assert 403 when called without ADMIN role

---

## Task Group 3: Integration Test & Demo Script (Score2)

3.1 Create `PipelineDemoIT` in `backend/src/test/java/.../pipeline/`:
  - Profiles: `{"test", "fmpkey"}`
  - Testcontainers Redis (`@Container`)
  - H2 in-memory datasource (via `test` profile)
  - Steps:
    1. `POST /auth/login` → capture JWT
    2. `POST /api/v1/admin/pipeline-run` body `{"tickers":["AAPL"]}` with `Authorization: Bearer <jwt>`
    3. Assert HTTP 200
    4. Assert response array length = 1
    5. Assert `totalScore` is non-null and > 0
    6. Assert `marginOfSafety` is non-null
    7. Assert `recommendation` is non-null and not `"ERROR:..."`

3.2 Create `scripts/pipeline-demo.sh`:
  - Bash script with `#!/usr/bin/env bash`
  - Configurable `BASE_URL` (default `http://localhost:8080`)
  - Step 1: login → extract token via `jq`
  - Step 2: POST pipeline-run with `tickers=AAPL,MSFT,KO,JNJ`
  - Step 3: pretty-print ranked table via `jq` (symbol, totalScore, marginOfSafety, recommendation)
  - Step 4: logout
  - Header comment block explaining prerequisites (running backend, FMP key set, `jq` installed)

---

## Task Group 4: Review & Merge Readiness

4.1 Run all existing tests to confirm no regressions (`mvn test -P test`)
4.2 Verify `PipelineDemoIT` passes with real FMP key (`mvn test -P test,fmpkey -Dtest=PipelineDemoIT`)
4.3 Run `scripts/pipeline-demo.sh` against local running backend — confirm ranked output printed
4.4 Merge `phase/score-demo` → `main` via `/merge-phase`
