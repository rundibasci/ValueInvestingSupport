# Validation — Group Score: Full Pipeline Demo

## Definition of Done

Group Score is complete and ready to merge when all checks below pass.

---

## 1. Unit Tests Pass — ValueScoreService

`ValueScoreServiceTest` must cover:

| Test case | Expected behaviour |
|---|---|
| MoS = 0.25 (25%) | MoS sub-score ≈ 50 (mid-range) |
| MoS = 0.50 (50%) | MoS sub-score = 100 (capped) |
| MoS = 0.00 | MoS sub-score = 0 |
| `dividendYield` = null | Dividend sub-score = 0, no exception |
| `payoutRatio` > 80% | Dividend sub-score = 0 even if yield is non-null |
| All inputs null / empty history | Returns `ValueScore` with `totalScore` = 0, no exception |
| Full known input set | `totalScore` matches hand-calculated reference value |

Run: `mvn test -pl backend` — all tests green, including pre-existing suite.

---

## 2. Integration Test Passes — PipelineDemoIT

Prerequisites:
```bash
docker compose -f docker-compose.demo.yml up -d   # Redis
export FMP_API_KEY=<your-key>
```

Run:
```bash
mvn verify -Dgroups=integration
```

### Assertions in `PipelineDemoIT`:

```
POST /api/v1/admin/pipeline-run  { "tickers": ["AAPL"] }
→ HTTP 200
→ body.results.size() ≥ 1
→ body.results[0].symbol == "AAPL"
→ body.results[0].totalScore is non-null, 0 ≤ totalScore ≤ 100
→ body.results[0].marginOfSafety is non-null
→ body.errors is empty
```

All assertions must be green.

---

## 3. Demo Script Runs End-to-End

With the application running (`spring.profiles.active=localstack`) and Redis up:

```bash
FMP_API_KEY=<key> ./scripts/pipeline-demo.sh
```

Expected output: formatted JSON ranked table with at least `AAPL` present, `totalScore` populated.

---

## 4. DB State Verified

After `POST /api/v1/admin/pipeline-run` completes, the H2 console (or `application-local` with PostgreSQL) must show:

- `fundamental_snapshot` rows for each ticker
- `ratio_snapshot` rows for each ticker
- `valuation_result` rows for each ticker
- `value_score` rows for each ticker — confirming persistence

---

## 5. Pre-existing Tests Unaffected

```bash
mvn test
```

All 107+ pre-existing unit tests must remain green. `ValueScoreService` must not break existing beans.

---

## 6. D1 Compatibility Confirmed

`ValueScoreService.compute(...)` signature must match what D1 will use. Verify:
- The `ValueScore` entity is populated (not just computed in-memory)
- `ValueScoreRepository.save(score)` is called within `PipelineRunService`
- D1's planned `GET /api/v1/securities/{symbol}/score` could read the persisted record with no changes to `ValueScoreService`

---

## Merge Checklist

- [ ] `ValueScoreServiceTest` all cases green
- [ ] `mvn test` (no integration) fully green — no pre-existing tests broken
- [ ] `PipelineDemoIT` green with Docker Redis + valid `FMP_API_KEY`
- [ ] `scripts/pipeline-demo.sh` runs end-to-end, outputs ranked JSON
- [ ] `value_score` rows present in DB after pipeline run
- [ ] `ValueScoreService` signature compatible with D1 plan (no refactor needed at D1 merge)
- [ ] PR description includes sample pipeline-run response JSON as evidence
