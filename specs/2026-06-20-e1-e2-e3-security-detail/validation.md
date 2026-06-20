# Validation — Group E: Security Detail API (M5)

## How to know this phase is done and can be merged

### 1. Unit tests pass

```bash
mvn test -pl backend -Dtest="SecuritySearchControllerTest,SecurityProfileControllerTest,FinancialsControllerTest,RatiosControllerTest,DividendsControllerTest,InsidersControllerTest,GrowthControllerTest,PeersControllerTest,SecurityValuationControllerTest"
```

| Test class | Key assertions |
|---|---|
| `SecuritySearchControllerTest` | 200 + results array for `?q=AAPL`; empty array for blank `q`; 401 unauthenticated |
| `SecurityProfileControllerTest` | 200 known symbol; 404 unknown; 422 when snapshot > 7 days old; 401 unauthenticated |
| `FinancialsControllerTest` | 200 with `annuals.size ≤ 10`, `quarters.size ≤ 8`, `ttm` non-null; 404; 401 |
| `RatiosControllerTest` | 200 with `ratios.size ≤ 10`; 404; 401 |
| `DividendsControllerTest` | 200 with `streak ≥ 0`; `cagr3y` null when service returns null; 404; 401 |
| `InsidersControllerTest` | 200 with `trades` list (may be empty); 404; 401 |
| `GrowthControllerTest` | 200 with `revenue.cagr3y` non-null when ≥ 4 snapshots; null when fewer; 404; 401 |
| `PeersControllerTest` | 200 with `peers` list (may be empty); 404; 401 |
| `SecurityValuationControllerTest` | 200 with `disclaimer`; `analystEstimates` null when empty; populated when 3 estimates seeded; `ddmValue` null acceptable; 404; 422 no valuation; 401 |

### 2. Full test suite passes (no regressions)

```bash
mvn test -pl backend
```

All pre-existing tests continue to pass.

### 3. Integration test passes with Testcontainers PostgreSQL

```bash
mvn test -pl backend -Dtest=SecurityDetailIT
```

All assertions must pass:

| Endpoint | Assertion |
|---|---|
| `GET /search?q=AAPL` | HTTP 200; result array contains `{ symbol: "AAPL" }` |
| `GET /search?q=apple` | HTTP 200; result array contains AAPL (case-insensitive name match) |
| `GET /AAPL` | HTTP 200; `companyName = "Apple Inc."`, `sector = "Technology"` |
| `GET /UNKNOWN` | HTTP 404 |
| `GET /AAPL/financials` | HTTP 200; `annuals.size() = 10`; `quarters.size() = 8`; `ttm` non-null |
| `GET /AAPL/ratios` | HTTP 200; `ratios.size() = 10` |
| `GET /AAPL/dividends` | HTTP 200; `streak = 5`; `cagr3y` non-null |
| `GET /AAPL/insiders` | HTTP 200; `trades.size() = 3` |
| `GET /AAPL/growth` | HTTP 200; `revenue.cagr3y` non-null (11 annual snapshots seeded) |
| `GET /AAPL/peers` | HTTP 200; `peers` list contains `{ symbol: "MSFT" }` |
| `GET /AAPL/valuation` | HTTP 200; `compositeFairValue` non-null; `disclaimer` = `"This is a decision-support tool, not investment advice (MiFID II)."`; `analystEstimates.analystCount = 3`; `analystEstimates.consensus = "BUY"` |
| Any endpoint, no token | HTTP 401 |

### 4. Flyway migrations apply cleanly

```bash
mvn flyway:migrate -pl backend
```

No errors. Both migrations applied in order:
- `V{N}__security_detail_indexes.sql` — 5 indexes created: `idx_security_name`, `idx_fundamental_snapshot_security_period_date`, `idx_ratio_snapshot_security_date`, `idx_dividend_record_security_date`, `idx_insider_trade_security_date`
- `V{N+1}__analyst_estimate.sql` — `analyst_estimate` table created + `idx_analyst_estimate_security_date`

### 5. Manual API smoke test

With backend running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`):

```bash
# Step 1 — login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

# Step 2 — search autocomplete
curl -s "http://localhost:8080/api/v1/securities/search?q=AAPL" \
  -H "Authorization: Bearer $TOKEN" | jq '.[0].symbol'
# Expected: "AAPL"

# Step 3 — full profile
curl -s http://localhost:8080/api/v1/securities/AAPL \
  -H "Authorization: Bearer $TOKEN" | jq '{companyName, sector, currentPrice, dataAsOf}'
# Expected: companyName non-null; currentPrice > 0; dataAsOf is a date string

# Step 4 — financials
curl -s http://localhost:8080/api/v1/securities/AAPL/financials \
  -H "Authorization: Bearer $TOKEN" \
  | jq '{annualCount: (.annuals | length), quarterCount: (.quarters | length), hasTtm: (.ttm != null)}'
# Expected: annualCount 1–10; quarterCount 0–8; hasTtm: true

# Step 5 — ratios
curl -s http://localhost:8080/api/v1/securities/AAPL/ratios \
  -H "Authorization: Bearer $TOKEN" | jq '.ratios | length'
# Expected: 1–10

# Step 6 — dividends
curl -s http://localhost:8080/api/v1/securities/AAPL/dividends \
  -H "Authorization: Bearer $TOKEN" | jq '{streak, cagr3y}'
# Expected: streak ≥ 0; cagr3y is a number or null

# Step 7 — insiders
curl -s http://localhost:8080/api/v1/securities/AAPL/insiders \
  -H "Authorization: Bearer $TOKEN" | jq '.trades | length'
# Expected: ≥ 0

# Step 8 — growth
curl -s http://localhost:8080/api/v1/securities/AAPL/growth \
  -H "Authorization: Bearer $TOKEN" | jq '{revenueCagr3y: .revenue.cagr3y, fcfCagr5y: .fcf.cagr5y}'
# Expected: numbers or null depending on ingested history

# Step 9 — peers
curl -s http://localhost:8080/api/v1/securities/AAPL/peers \
  -H "Authorization: Bearer $TOKEN" | jq '.peers | length'
# Expected: 0–5

# Step 10 — full valuation
curl -s http://localhost:8080/api/v1/securities/AAPL/valuation \
  -H "Authorization: Bearer $TOKEN" \
  | jq '{compositeFairValue, marginOfSafety, mosLow, mosHigh, disclaimer}'
# Expected: compositeFairValue non-null; disclaimer = MiFID II string
```

### 6. Authorization gate confirmed

```bash
# All 9 Security Detail endpoints require auth → 401 without token
for path in "search?q=AAPL" "AAPL" "AAPL/financials" "AAPL/ratios" \
            "AAPL/dividends" "AAPL/insiders" "AAPL/growth" "AAPL/peers" "AAPL/valuation"; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "http://localhost:8080/api/v1/securities/$path")
  echo "$path → $STATUS"
done
# Expected: all → 401
```

### 7. Stale data guard confirmed

Insert a `fundamental_snapshot` row with `snapshot_date = CURRENT_DATE - 8` for a test ticker. Then call `GET /api/v1/securities/{ticker}`. Verify:
- HTTP 422
- Response body contains `"stale"` or the snapshot date in the error message

### 8. Analyst estimates aggregation confirmed

With 3 analyst estimates seeded (ratings: HOLD, BUY, BUY; prices: 190, 200, 220), call `GET /api/v1/securities/{symbol}/valuation`. Verify:
- `analystEstimates.analystCount = 3`
- `analystEstimates.priceTargetLow = 190.00`
- `analystEstimates.priceTargetHigh = 220.00`
- `analystEstimates.priceTargetMean` ≈ 203.33
- `analystEstimates.consensus = "BUY"` (2 BUY vs 1 HOLD)

Then verify with no estimates seeded: `analystEstimates` field is `null` in response.

---

## Merge criteria

All 8 checkpoints above pass. No `@Disabled` tests. `SecurityDetailIT` passes on two consecutive runs without flakiness. Both Flyway migrations apply without errors on a clean schema. The branch merges cleanly into `main`.
