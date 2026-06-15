# Validation — Phase B2: Redis Cache Layer

## Definition of Done

B2 is complete and ready to merge when all checks below pass.

---

## 1. Unit Tests Pass

| Test class | What it verifies |
|---|---|
| `MarketDataClientCacheTest` | Second call to `getQuote` / `getProfile` / `getFundamentals` / `getRatios` does not invoke the underlying HTTP client (spy invocation count = 1) |
| `CacheEvictionServiceTest` | After `evictSymbol("AAPL")`, all four cache names contain no entry for `AAPL` |

Run: `mvn test -pl backend` — both test classes must be green.

---

## 2. Integration Test Passes (live Redis)

`RedisCacheIT` must pass end-to-end with a running Redis instance (Testcontainers or `docker compose up redis`).

### Steps the test asserts:

```
1. GET /api/v1/securities/AAPL/quote   (authenticated, any role)
   → HTTP 200; Redis key mdc:fmp:quote:AAPL now exists

2. Redis CLI: TTL mdc:fmp:quote:AAPL
   → value between 850 and 900 (seconds)

3. GET /api/v1/securities/AAPL/quote   (second call, same session)
   → HTTP 200; underlying FmpMarketDataClient#getQuote invocation count still = 1 (cache hit)

4. DELETE /api/v1/admin/cache/AAPL     (ADMIN token)
   → HTTP 204

5. Redis CLI: EXISTS mdc:fmp:quote:AAPL
   → 0  (key removed)
```

---

## 3. Manual Smoke Test (Redis CLI)

For human sign-off before merge:

```bash
# 1. Start stack
docker compose up -d

# 2. Obtain token (admin user from A3)
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin_pass>"}' | jq -r .accessToken)

# 3. Trigger a quote fetch (populates cache)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/securities/AAPL/quote | jq .

# 4. Confirm cache key exists with correct TTL
docker compose exec redis redis-cli TTL mdc:fmp:quote:AAPL
# Expected: ~900

# 5. Confirm profile key exists with 24h TTL
docker compose exec redis redis-cli TTL mdc:fmp:profile:AAPL
# Expected: ~86400

# 6. Evict
curl -s -X DELETE -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/cache/AAPL
# Expected: HTTP 204

# 7. Confirm eviction
docker compose exec redis redis-cli EXISTS mdc:fmp:quote:AAPL
# Expected: 0
```

---

## 4. Non-Redis Tests Unaffected

`mvn test` (without `-DskipITs`) must complete with no failures in pre-existing B1 test classes. The `application-test.yml` in-memory cache profile must prevent any unit test from requiring a live Redis connection.

---

## Merge Checklist

- [ ] All unit tests green (`mvn test`)
- [ ] `RedisCacheIT` green with live Redis
- [ ] Manual smoke test steps 4 and 7 confirmed in Redis CLI
- [ ] No pre-existing B1 tests broken
- [ ] PR description includes Redis CLI output as evidence
