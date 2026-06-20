# Validation — Group F2: Portfolio CRUD & Holdings (M6)

## How to know this phase is done and can be merged

### 1. Unit tests pass

```bash
mvn test -pl backend -Dtest="PortfolioControllerTest"
```

| Test class | Key assertions |
|---|---|
| `PortfolioControllerTest` | `GET /portfolios` → 200 + array; empty list → `[]`; `POST` valid → 201 with `id` and `holdingCount = 0`; `POST` blank name → 400; `GET /{id}` → 200 + holdings + totalValue; `GET /{id}` unknown → 404; `POST /{id}/holdings` valid → 201; `POST /{id}/holdings` blank symbol → 400; `POST /{id}/holdings` null quantity → 400; `PUT /{id}/holdings/{hid}` → 200 updated quantity; `PUT` unknown → 404; `DELETE /{id}/holdings/{hid}` → 204; `DELETE` unknown → 404; unauthenticated → 401 |

### 2. Full test suite passes (no regressions)

```bash
mvn test -pl backend
```

All pre-existing tests (WatchlistIT, SecurityDetailIT, ScreenerIT, etc.) continue to pass.

### 3. Integration test passes with Testcontainers PostgreSQL

```bash
mvn test -pl backend -Dtest=PortfolioIT
```

All assertions must pass:

| Step | Action | Assertion |
|---|---|---|
| 1 | `POST /auth/login` admin/admin | HTTP 200; `accessToken` in response |
| 2 | `GET /api/v1/portfolios` | HTTP 200; body is `[]` |
| 3 | `POST /api/v1/portfolios` `{ "name": "Growth Portfolio", "description": "Long-term holds" }` | HTTP 201; `id` non-null; `name = "Growth Portfolio"`; `holdingCount = 0` |
| 4 | `GET /api/v1/portfolios` | HTTP 200; array size = 1 |
| 5 | `POST /api/v1/portfolios/{portfolioId}/holdings` `{ "symbol": "aapl", "quantity": 10, "averageCostBasis": 150.00, "currency": "USD" }` | HTTP 201; `symbol = "AAPL"` (uppercased); `quantity = 10`; `currentPrice = null` |
| 6 | `POST /api/v1/portfolios/{portfolioId}/holdings` `{ "symbol": "MSFT", "quantity": 5 }` | HTTP 201; `symbol = "MSFT"`; `averageCostBasis = null`; `currentPrice = null` |
| 7 | `GET /api/v1/portfolios/{portfolioId}` | HTTP 200; `holdings` size = 2; `totalValue = null`; `weightedMoS = null` |
| 8 | Seed via `JdbcTemplate`: security AAPL + price_quote `close=180.00` (today) + valuation_result `composite_fair_value=210.00, margin_of_safety=16.67, recommendation=QUALITY_VALUE` | — |
| 9 | `GET /api/v1/portfolios/{portfolioId}` | HTTP 200; AAPL: `currentPrice = 180.00`; `currentValue = 1800.00`; `compositeFairValue = 210.00`; `marginOfSafety = 16.67`; `recommendation = "QUALITY_VALUE"`; MSFT: `currentPrice = null`; `totalValue = 1800.00`; AAPL `weightPercent = 100.00`; `weightedMoS = 16.67` |
| 10 | `PUT /api/v1/portfolios/{portfolioId}/holdings/{holdingId1}` `{ "quantity": 15, "averageCostBasis": 145.00, "currency": "USD" }` | HTTP 200; `quantity = 15`; `averageCostBasis = 145.00`; `currentPrice = 180.00` |
| 11 | `DELETE /api/v1/portfolios/{portfolioId}/holdings/{holdingId2}` | HTTP 204 |
| 12 | `GET /api/v1/portfolios/{portfolioId}` | HTTP 200; `holdings` size = 1; only AAPL remains; `quantity = 15`; `currentValue = 2700.00` |
| 13 | `POST /api/v1/portfolios` `{ "name": "Dividend Portfolio" }` | HTTP 201; new portfolio created |
| 14 | `GET /api/v1/portfolios` | HTTP 200; array size = 2 |
| 15 | `GET /api/v1/portfolios` (no token) | HTTP 401 |
| 16 | `GET /api/v1/portfolios/{unknown_uuid}` | HTTP 404 |
| 17 | `DELETE /api/v1/portfolios/{portfolioId}/holdings/{unknown_uuid}` | HTTP 404 |

### 4. Flyway migration applies cleanly

```bash
mvn flyway:migrate -pl backend
```

No errors. Migration `V8__portfolio_holding_index.sql` applied:
- Index `idx_holding_portfolio` created on `holding(portfolio_id)`
- Index `idx_holding_portfolio_symbol` created on `holding(portfolio_id, symbol)`

### 5. Manual API smoke test

With backend running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`):

```bash
# Step 1 — login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

# Step 2 — empty portfolio list
curl -s http://localhost:8080/api/v1/portfolios \
  -H "Authorization: Bearer $TOKEN" | jq 'length'
# Expected: 0

# Step 3 — create portfolio
PORTFOLIO_ID=$(curl -s -X POST http://localhost:8080/api/v1/portfolios \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"My Portfolio","description":"Test"}' | jq -r .id)
echo "Portfolio ID: $PORTFOLIO_ID"
# Expected: valid UUID

# Step 4 — list portfolios
curl -s http://localhost:8080/api/v1/portfolios \
  -H "Authorization: Bearer $TOKEN" | jq '[.[] | {id, name, holdingCount}]'
# Expected: [{id: ..., name: "My Portfolio", holdingCount: 0}]

# Step 5 — add holding
HOLDING_ID=$(curl -s -X POST "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/holdings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"aapl","quantity":10,"averageCostBasis":150}' | jq -r .id)
echo "Holding ID: $HOLDING_ID"
# Expected: valid UUID; symbol = "AAPL"

# Step 6 — add second holding
curl -s -X POST "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/holdings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"MSFT","quantity":5}' | jq '{id, symbol, quantity}'
# Expected: symbol = "MSFT", quantity = 5

# Step 7 — portfolio detail (prices may be null if DB is empty)
curl -s "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '{totalValue, weightedMoS, holdingsCount: (.holdings | length)}'
# Expected: holdingsCount = 2

# Step 8 — update holding
curl -s -X PUT "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/holdings/$HOLDING_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"quantity":15,"averageCostBasis":145}' | jq '{quantity, averageCostBasis}'
# Expected: quantity = 15; averageCostBasis = 145.0

# Step 9 — remove holding
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/holdings/$HOLDING_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 204

# Step 10 — only MSFT remains
curl -s "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '.holdings | length'
# Expected: 1
```

### 6. Authorization gate confirmed

```bash
# All endpoints require auth → 401 without token
for path in "" "/$PORTFOLIO_ID" "/$PORTFOLIO_ID/holdings"; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "http://localhost:8080/api/v1/portfolios$path")
  echo "GET /api/v1/portfolios$path → $STATUS"
done
# Expected: all → 401

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/v1/portfolios \
  -H "Content-Type: application/json" \
  -d '{"name":"test"}'
# Expected: 401
```

### 7. Ownership guard confirmed

With two users (admin + a second user if seeded), a `GET /api/v1/portfolios/{admin_portfolio_id}` using the second user's token must return 404.

```bash
# Add a holding to admin's portfolio, then attempt access with no-token (simulates wrong user)
curl -s -o /dev/null -w "%{http_code}" \
  "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID"
# Expected: 401 (no token) — ownership enforced before data is revealed
```

### 8. Symbol uppercasing confirmed

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

PORTFOLIO_ID=$(curl -s -X POST http://localhost:8080/api/v1/portfolios \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"Symbol Test"}' | jq -r .id)

curl -s -X POST "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/holdings" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"ko","quantity":20}' | jq .symbol
# Expected: "KO"
```

---

## Merge criteria

All 8 checkpoints above pass. No `@Disabled` tests. `PortfolioIT` passes on two consecutive runs without flakiness. Flyway migration V8 applies without errors on a clean schema. The branch merges cleanly into `main`.
