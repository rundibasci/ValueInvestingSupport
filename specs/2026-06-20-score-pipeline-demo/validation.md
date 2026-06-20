# Validation — Group Score: Pipeline Demo (M3.8)

## How to know this phase is done and can be merged

### 1. Unit tests pass

```bash
mvn test -pl backend -Dtest="ValueScoreServiceTest,PipelineControllerTest"
```

- `ValueScoreServiceTest`: all sub-score assertions pass, null-fallback path covered
- `PipelineControllerTest`: 200 + correct JSON shape, 403 without ADMIN role

### 2. Full test suite passes (no regressions)

```bash
mvn test -pl backend
```

All pre-existing tests continue to pass.

### 3. Integration test passes with real FMP key

```bash
mvn test -pl backend -Dtest=PipelineDemoIT -Dspring.profiles.active=test,fmpkey
```

- HTTP 200 returned
- Response array contains 1 item (AAPL)
- `totalScore` > 0
- `marginOfSafety` is non-null
- `recommendation` is not null and does not start with `"ERROR:"`

### 4. Demo script runs end-to-end

With backend running locally (`mvn spring-boot:run -Dspring-boot.run.profiles=local`):

```bash
bash scripts/pipeline-demo.sh
```

Expected output: a ranked table of 4 tickers (AAPL, MSFT, KO, JNJ) with `totalScore`, `marginOfSafety`, and `recommendation` columns, sorted by score descending.

### 5. Manual API smoke test

```bash
# login
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq .accessToken

# pipeline-run (replace TOKEN)
curl -s -X POST http://localhost:8080/api/v1/admin/pipeline-run \
  -H "Authorization: Bearer TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tickers":["AAPL"]}' | jq .
```

Expected: array with 1 element; `totalScore` between 0 and 100; `recommendation` one of `QUALITY_VALUE`, `UNDERVALUED`, `FAIRLY_VALUED`, `OVERVALUED`, `AVOID`.

### 6. Authorization gate confirmed

```bash
# call without token — must return 401
curl -s -o /dev/null -w "%{http_code}" \
  -X POST http://localhost:8080/api/v1/admin/pipeline-run \
  -H "Content-Type: application/json" \
  -d '{"tickers":["AAPL"]}'
# → 401

# call with non-admin token — must return 403
# (login as a non-ADMIN user first, then call)
```

### 7. ValueScore persisted to DB

After the pipeline-run call, verify a row exists in the `value_score` table for AAPL:

```sql
SELECT symbol, total_score, score_date
FROM value_score vs
JOIN security s ON s.id = vs.security_id
WHERE s.symbol = 'AAPL'
ORDER BY score_date DESC
LIMIT 1;
```

Row must exist with `total_score` between 0 and 100.

---

## Merge criteria

All 7 checkpoints above pass. No skipped or `@Disabled` tests. `scripts/pipeline-demo.sh` is committed and executable (`chmod +x`). The branch merges cleanly into `main`.
