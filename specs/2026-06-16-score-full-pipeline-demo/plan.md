# Plan — Group Score: Full Pipeline Demo

## Group 1 — ValueScoreService

1. Create `it.mazzoni.vis.scoring.ValueScoreService`:
   - Method signature: `ValueScore compute(String symbol, ValuationResult valuation, RatioSnapshot ratios, List<FundamentalSnapshot> history)`
   - Compute five sub-scores (each 0–100, null-safe):
     - **MoS Score (30%)**: `marginOfSafety` → linear scale (0 % MoS = 0, 50 % MoS = 100, capped)
     - **Quality Score (25%)**: average of normalized `roic`, `roe`, `grossMargin`
     - **Safety Score (20%)**: normalized inverse `debtToEquity` + normalized `currentRatio`
     - **Growth Score (15%)**: revenue YoY growth from 2 most-recent `FundamentalSnapshot` records
     - **Dividend Score (10%)**: normalized `dividendYield` if `payoutRatio` < 80%, else 0
   - `totalScore = Σ(subScore × weight)`, rounded to 2 decimal places
   - Populate and return a `ValueScore` entity (does **not** persist — caller persists)

2. Unit test `ValueScoreServiceTest`:
   - Known inputs → verify each sub-score independently
   - Null `dividendYield` → dividend sub-score = 0, no exception
   - `marginOfSafety` = 0.25 → MoS sub-score ≈ 50 (verify formula)
   - Run: `mvn test -pl backend` must be green

## Group 2 — PipelineRunController & PipelineRunService

3. Create `it.mazzoni.vis.admin.dto.PipelineRunRequest` record:
   ```java
   public record PipelineRunRequest(@NotEmpty List<@NotBlank String> tickers) {}
   ```

4. Create `it.mazzoni.vis.admin.dto.PipelineRunResult` record:
   ```java
   public record PipelineRunResult(
       List<PipelineTickerResult> results,
       List<String> errors
   ) {}
   ```
   and `PipelineTickerResult` with fields: `symbol`, `companyName`, `compositeFairValue`,
   `currentPrice`, `marginOfSafety`, `totalScore`, `recommendation`.

5. Create `it.mazzoni.vis.admin.PipelineRunService`:
   - Inject `MarketDataClient`, `ValuationService`, `ValueScoreService`, `ValueScoreRepository`,
     `FundamentalSnapshotRepository`, `RatioSnapshotRepository`
   - For each ticker:
     a. Fetch + persist profile, fundamentals, ratios, price via `MarketDataClient`
     b. Call `ValuationService.calculate(symbol)` → persist `ValuationResult`
     c. Load latest `RatioSnapshot` + last 3 `FundamentalSnapshot` from DB
     d. Call `ValueScoreService.compute(...)` → persist `ValueScore`
     e. Map to `PipelineTickerResult`
   - Per-ticker exceptions are caught, symbol added to `errors` list (partial success)
   - Sort results by `totalScore DESC` before returning

6. Add `POST /api/v1/admin/pipeline-run` to a new `PipelineRunController`:
   - `@PreAuthorize("hasRole('ADMIN')")` or secured via `SecurityConfig` route pattern
   - Delegates to `PipelineRunService`
   - Returns `ResponseEntity<PipelineRunResult>`
   - Validates request with `@Valid`; returns `400` if `tickers` is empty

## Group 3 — Integration Test

7. Create `PipelineDemoIT` in `src/test/java/.../admin/`:
   - `@Tag("integration")`, `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("localstack")`
   - Login as `admin@localstack.local` / `admin` → extract `accessToken`
   - `POST /api/v1/admin/pipeline-run` with `{ "tickers": ["AAPL"] }`
     → assert HTTP 200
     → assert `results` list has size ≥ 1
     → assert `results[0].totalScore` is non-null and between 0 and 100
     → assert `results[0].marginOfSafety` is non-null
     → assert `errors` list is empty (AAPL expected to resolve from FMP)
   - Note: requires Redis (`docker compose -f docker-compose.demo.yml up -d`) and valid `FMP_API_KEY`

## Group 4 — Demo Script

8. Create `scripts/pipeline-demo.sh`:
   ```bash
   #!/usr/bin/env bash
   # Full Pipeline Demo — login → seed → valuate → score → ranked table
   # Usage: FMP_API_KEY=xxx ./scripts/pipeline-demo.sh

   BASE_URL="${BASE_URL:-http://localhost:8080}"
   TICKERS="${TICKERS:-AAPL,MSFT,KO,JNJ}"

   TOKEN=$(curl -s -X POST "$BASE_URL/auth/login" \
     -H 'Content-Type: application/json' \
     -d '{"email":"admin@localstack.local","password":"admin"}' | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

   echo "Token acquired. Running pipeline for: $TICKERS"

   TICKER_JSON=$(echo "$TICKERS" | sed 's/,/","/g; s/^/["/ ; s/$/"]/  ')

   curl -s -X POST "$BASE_URL/api/v1/admin/pipeline-run" \
     -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' \
     -d "{\"tickers\": $TICKER_JSON}" | python3 -m json.tool
   ```
   - Make executable: `chmod +x scripts/pipeline-demo.sh`
   - Document in script header: prerequisites (Docker Redis, `FMP_API_KEY` set, app running on port 8080)
