# Validation — Group F1: Watchlist (M6 start)

## How to know this phase is done and can be merged

### 1. Unit tests pass

```bash
mvn test -pl backend -Dtest="WatchlistControllerTest"
```

| Test class | Key assertions |
|---|---|
| `WatchlistControllerTest` | `GET /watchlist` → 200 + array; empty list → `[]`; `POST` valid → 201; `POST` blank symbol → 400; `POST` duplicate → 409; `PUT` → 200 + updated thresholds; `PUT` unknown → 404; `DELETE` → 204; `DELETE` unknown → 404; `GET /watchlist/alerts` → 200 + array; no alerts → `[]`; unauthenticated → 401 |

### 2. Full test suite passes (no regressions)

```bash
mvn test -pl backend
```

All pre-existing tests (SecurityDetailIT, ScreenerIT, etc.) continue to pass.

### 3. Integration test passes with Testcontainers PostgreSQL

```bash
mvn test -pl backend -Dtest=WatchlistIT
```

All assertions must pass:

| Step | Action | Assertion |
|---|---|---|
| 1 | `POST /auth/login` admin/admin | HTTP 200; `accessToken` in response |
| 2 | `GET /api/v1/watchlist` | HTTP 200; body is `[]` |
| 3 | `POST /api/v1/watchlist` `{ "symbol": "AAPL" }` | HTTP 201; `id` non-null; `symbol = "AAPL"`; `addedAt` non-null; `mosAlertMin = null` |
| 4 | `POST /api/v1/watchlist` `{ "symbol": "AAPL" }` (duplicate) | HTTP 409 |
| 5 | `GET /api/v1/watchlist` | HTTP 200; array size = 1; first item `symbol = "AAPL"` |
| 6 | `PUT /api/v1/watchlist/{aapl_id}` `{ "mosAlertMin": 10.0, "mosAlertMax": 25.0 }` | HTTP 200; `mosAlertMin = 10.0`; `mosAlertMax = 25.0` |
| 7 | `POST /api/v1/watchlist` `{ "symbol": "MSFT", "fundamentalDegradeThreshold": 70.0 }` | HTTP 201; `fundamentalDegradeThreshold = 70.0` |
| 8 | `GET /api/v1/watchlist` | HTTP 200; array size = 2; first item is MSFT (newest-first order) |
| 9 | `DELETE /api/v1/watchlist/{aapl_id}` | HTTP 204 |
| 10 | `GET /api/v1/watchlist` | HTTP 200; array size = 1; only MSFT remains |
| 11 | Seed Alert via `JdbcTemplate`: `status=ACTIVE, alert_type=MOS_ENTRY, symbol=MSFT` for admin user | — |
| 12 | `GET /api/v1/watchlist/alerts` | HTTP 200; array size = 1; `alertType = "MOS_ENTRY"`; `symbol = "MSFT"` |
| 13 | `GET /api/v1/watchlist` (no token) | HTTP 401 |
| 14 | `DELETE /api/v1/watchlist/{unknown_uuid}` | HTTP 404 |

### 4. Flyway migration applies cleanly

```bash
mvn flyway:migrate -pl backend
```

No errors. Migration `V7__watchlist_fundamental_degrade.sql` applied:
- Column `fundamental_degrade_threshold DECIMAL(10,4)` added to `watchlist_item`
- Index `idx_watchlist_user` created on `watchlist(user_id)`

### 5. Manual API smoke test

With backend running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`):

```bash
# Step 1 — login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

# Step 2 — empty watchlist
curl -s http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" | jq 'length'
# Expected: 0

# Step 3 — add AAPL
curl -s -X POST http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","mosAlertMin":10,"mosAlertMax":30}' | jq '{id, symbol, mosAlertMin}'
# Expected: id is a UUID string; symbol = "AAPL"; mosAlertMin = 10.0

# Step 4 — list items
curl -s http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" | jq '[.[] | {symbol, mosAlertMin, mosAlertMax}]'
# Expected: [{symbol: "AAPL", mosAlertMin: 10.0, mosAlertMax: 30.0}]

# Step 5 — update thresholds (replace {id} with UUID from Step 3)
ITEM_ID=$(curl -s http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')
curl -s -X PUT "http://localhost:8080/api/v1/watchlist/$ITEM_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"mosAlertMin":15,"fundamentalDegradeThreshold":70}' | jq '{mosAlertMin, fundamentalDegradeThreshold}'
# Expected: mosAlertMin = 15.0; fundamentalDegradeThreshold = 70.0

# Step 6 — delete AAPL
curl -s -o /dev/null -w "%{http_code}" \
  -X DELETE "http://localhost:8080/api/v1/watchlist/$ITEM_ID" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 204

# Step 7 — watchlist now empty
curl -s http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" | jq 'length'
# Expected: 0

# Step 8 — active alerts (may be empty if G1 has not run)
curl -s http://localhost:8080/api/v1/watchlist/alerts \
  -H "Authorization: Bearer $TOKEN" | jq 'length'
# Expected: >= 0
```

### 6. Authorization gate confirmed

```bash
# All 5 endpoints require auth → 401 without token
for path in "" "/alerts"; do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    "http://localhost:8080/api/v1/watchlist$path")
  echo "GET /api/v1/watchlist$path → $STATUS"
done
# Expected: both → 401

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/v1/watchlist \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL"}'
# Expected: 401
```

### 7. Duplicate symbol guard confirmed

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

curl -s -X POST http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"KO"}'
# Expected: 201

curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/v1/watchlist \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"KO"}'
# Expected: 409
```

### 8. Ownership enforcement confirmed

With two users (admin + a second user if seeded), a `DELETE /api/v1/watchlist/{id}` using the second user's token against an item belonging to admin must return 404.

---

## Merge criteria

All 8 checkpoints above pass. No `@Disabled` tests. `WatchlistIT` passes on two consecutive runs without flakiness. Flyway migration V7 applies without errors on a clean schema. The branch merges cleanly into `main`.
