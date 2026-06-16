# Validation — Phase B3: Data Ingestion Jobs

## Definition of Done

B3 is complete and ready to merge when all checks below pass.

---

## 1. Unit Tests Pass

| Test class | What it verifies |
|---|---|
| `JobRunLoggerTest` | Happy path: supplier returns `42` → `job_run_log` row with `status = SUCCESS`, `records_processed = 42`. Exception path: supplier throws → row with `status = FAILED`, `error_message` non-null; exception re-thrown to caller. |
| `IngestionJobHealthIndicatorTest` | All 7 jobs have recent `SUCCESS` rows within expected window → `Health.up()`. One job's latest row is `FAILED` → `Health.down()` with job name in detail map. One job has no rows (never run) → `Health.down()`. |

Run: `mvn test -pl backend` — both test classes must be green.

---

## 2. Integration Tests Pass (real FMP + live DB)

Run with a real `FMP_API_KEY` in environment:

```bash
mvn verify -pl backend -Dspring.profiles.active=test -DFMP_API_KEY=$FMP_API_KEY -Dit.test="BulkProfileSyncJobIT,QuoteRefreshJobIT"
```

| Test class | Assertions |
|---|---|
| `BulkProfileSyncJobIT` | After `bulkProfileSyncJob.run()`: ≥ 1 `Security` row in DB with non-null `sector` and `exchange`. `job_run_log` row exists with `job_name = 'bulk-profile-sync'` and `status = SUCCESS`. |
| `QuoteRefreshJobIT` | Seed `WatchlistItem(symbol = AAPL)`. After `quoteRefreshJob.run()`: `PriceQuote` row for AAPL with `price > 0`. `job_run_log` row with `job_name = 'quote-refresh'` and `status = SUCCESS`. |

---

## 3. Health Indicator Smoke Test

After at least one run of each job (manual trigger or waited for cron):

```bash
curl -s http://localhost:8080/actuator/health | jq .components.ingestionJobs
```

Expected output:

```json
{
  "status": "UP",
  "details": {
    "bulk-profile-sync":      "SUCCESS 2026-06-16T02:00:01Z (1842 records)",
    "bulk-fundamentals-sync": "SUCCESS 2026-06-16T03:00:05Z (1842 records)",
    "bulk-ratios-sync":       "SUCCESS 2026-06-16T03:30:03Z (1842 records)",
    "bulk-dcf-sync":          "SUCCESS 2026-06-16T04:00:04Z (1840 records)",
    "quote-refresh":          "SUCCESS 2026-06-16T10:15:01Z (12 records)",
    "dividend-update":        "SUCCESS 2026-06-16T06:00:02Z (12 records)",
    "insider-trading":        "SUCCESS 2026-06-16T10:00:01Z (12 records)"
  }
}
```

A `DOWN` result with detailed job names is acceptable as evidence of the indicator working, provided the unit test for the indicator itself is green.

---

## 4. Manual Ingestion Demo

Run `scripts/ingestion-demo.sh` against a local stack (`docker compose up -d`):

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .accessToken)

# 2. Trigger bulk profile sync manually
curl -s -X POST -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/admin/jobs/bulk-profile-sync/run
# Expected: HTTP 202

# 3. Wait ~30s, then check health
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/actuator/health | jq .components.ingestionJobs.status
# Expected: "UP"

# 4. Confirm Securities populated
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/v1/securities?exchange=NYSE&limit=5" | jq '[.[].symbol]'
# Expected: non-empty array of ticker symbols
```

The script output (or a screenshot of the terminal) must be included in the PR description as evidence.

---

## 5. Pre-existing Tests Unaffected

`mvn test` (unit tests only, no IT suffix) must complete with no regressions in B1 or B2 test classes. `app.jobs.enabled = false` in `application-test.yml` must prevent any `@Scheduled` annotation from firing during unit tests.

---

## Merge Checklist

- [ ] Unit tests green (`mvn test`)
- [ ] `BulkProfileSyncJobIT` green with real FMP key
- [ ] `QuoteRefreshJobIT` green
- [ ] `GET /actuator/health` shows `ingestionJobs` component (UP or detailed DOWN — indicator present)
- [ ] `scripts/ingestion-demo.sh` executed; PR description includes terminal output
- [ ] No B1 or B2 tests broken
- [ ] `app.jobs.enabled = false` confirmed in `application-test.yml`
