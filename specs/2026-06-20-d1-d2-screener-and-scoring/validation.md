# Validation — Group D: Value Score Engine & Stock Screener API (M4)

## How to know this phase is done and can be merged

### 1. Unit tests pass

```bash
mvn test -pl backend -Dtest="ScoreControllerTest,SecuritySpecificationTest,ScreenerControllerTest"
```

- `ScoreControllerTest`: 200 with existing score, 200 with on-demand compute, 404 for unknown symbol, 401 unauthenticated
- `SecuritySpecificationTest`: each filter predicate present when field non-null; absent when null
- `ScreenerControllerTest`: all four endpoints return correct shapes; 401 without auth

### 2. Full test suite passes (no regressions)

```bash
mvn test -pl backend
```

All pre-existing tests continue to pass.

### 3. Integration test passes with Testcontainers PostgreSQL

```bash
mvn test -pl backend -Dtest=ScreenerIT
```

All assertions must pass:

| Assertion | Expected |
|---|---|
| No-filter screener | `totalElements = 5000`, results sorted `totalScore DESC` |
| `minValueScore=80` | Every result item has `totalScore ≥ 80` |
| `sector="Technology"` | Every result item has `sector = "Technology"` |
| Graham preset filters | Every result: `marginOfSafety ≥ 15`, `debtToEquity ≤ 1.0` |
| Pagination `page=1, pageSize=10` | Exactly 10 results; `page=1` in response; symbols differ from `page=0` |
| `/screener/sectors` | Response includes `"Technology"` and `"Consumer Staples"` |
| `/screener/exchanges` | Response is a non-empty list |
| **Performance** | `POST /api/v1/screener` (no filters, 5 000 rows) completes in **< 500 ms** |

### 4. Flyway migration applies cleanly

```bash
mvn flyway:migrate -pl backend
```

No errors. All five indexes (`idx_value_score_security_date`, `idx_security_sector`, `idx_security_exchange`, `idx_ratio_snapshot_security_date`, `idx_valuation_result_security_date`) created without conflict.

### 5. Manual API smoke test

With backend running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`):

```bash
# Step 1 — login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

# Step 2 — score endpoint for a seeded symbol
curl -s http://localhost:8080/api/v1/securities/AAPL/score \
  -H "Authorization: Bearer $TOKEN" | jq '{totalScore, scoreDate}'
# Expected: totalScore between 0 and 100; scoreDate is today or recent

# Step 3 — screener (Graham preset filters)
curl -s -X POST http://localhost:8080/api/v1/screener \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"minMarginOfSafety":15,"maxDebtToEquity":1.0,"minRoic":10}' | jq '{totalElements, firstSymbol: .results[0].symbol}'
# Expected: totalElements ≥ 0; if any results, first item has highest totalScore

# Step 4 — presets
curl -s http://localhost:8080/api/v1/screener/presets \
  -H "Authorization: Bearer $TOKEN" | jq 'keys'
# Expected: ["dividend","graham","quality"]

# Step 5 — sectors list
curl -s http://localhost:8080/api/v1/screener/sectors \
  -H "Authorization: Bearer $TOKEN" | jq length
# Expected: > 0
```

### 6. Authorization gate confirmed

```bash
# No token → 401 on all screener endpoints
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/v1/screener \
  -H "Content-Type: application/json" \
  -d '{}'
# → 401

curl -s -o /dev/null -w "%{http_code}" \
  http://localhost:8080/api/v1/securities/AAPL/score
# → 401
```

### 7. Score on-demand compute path exercised

Call `GET /api/v1/securities/{symbol}/score` for a symbol that exists in the `security` table but has no `value_score` row. Verify:
- HTTP 200 returned
- `totalScore` is between 0 and 100
- A new row now exists in `value_score` for that symbol

### 8. N+1 query check

Enable SQL logging temporarily (`spring.jpa.show-sql=true`, `logging.level.org.hibernate.SQL=DEBUG`) and call `POST /api/v1/screener` with `pageSize=20`. Confirm the number of SQL statements is O(1) — a single paged query plus at most one count query — not O(n) per result row.

---

## Merge criteria

All 8 checkpoints above pass. No `@Disabled` tests. The < 500 ms assertion in `ScreenerIT` passes without flakiness on two consecutive runs. The branch merges cleanly into `main`.
