# Validation — Phase C3: Composite Fair Value & Margin of Safety

## Definition of Done

Phase C3 is complete and ready to merge when all of the following pass.

---

## 1. Tests Green

```bash
./mvnw test -pl backend -Dtest="ValuationServiceTest,ValuationControllerTest,CompositeWeightTest"
```

All tests in those classes must pass with zero failures.

Expected minimum test count: **~14 tests** across the three classes.

---

## 2. Reference Value Check

Inside `ValuationServiceTest` (or `CompositeWeightTest`), the following assertion must hold:

> **Given:** `dcfFairValue = 210.00`, `grahamNumber = 148.32`, `ddmFairValue = null`,
> configured weights `dcf=0.60, graham=0.25, ddm=0.15`
>
> **Effective weights:** `dcf = 60/85 ≈ 0.7059`, `graham = 25/85 ≈ 0.2941`
>
> **Expected `compositeFairValue`:**
> `210.00 × 0.7059 + 148.32 × 0.2941 ≈ 192.10` (rounded to 2dp)

The test must assert the exact BigDecimal value to 2 decimal places.

---

## 3. Fallback Paths Covered

Each of these cases must have a corresponding test:

| Scenario | Expected composite |
|---|---|
| DCF + Graham + DDM | Full weighted average |
| DCF + Graham (DDM absent) | Proportional: DCF 70.59%, Graham 29.41% |
| Graham only (DCF absent, DDM absent) | Graham 100% |
| All models absent | `ValuationNotApplicableException` thrown |

---

## 4. Endpoint Smoke Test (manual, optional for merge)

With the application running on the `local` profile (PostgreSQL + Redis via Docker Compose,
FMP data pre-seeded for AAPL via B3 or a manual insert):

```bash
# 1. Obtain a token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

# 2. Run valuation
curl -s -X POST http://localhost:8080/api/v1/securities/AAPL/valuation/dcf \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "wacc": 0.09,
    "growthY1Y5": 0.08,
    "growthY6Y10": 0.04,
    "terminalRate": 0.025
  }' | jq .
```

**Expected:** HTTP 200 with `compositeFairValue` non-null, `marginOfSafety` non-null,
`recommendation` set to one of `STRONG_BUY`, `QUALITY_VALUE`, `FAIR_VALUE`, `OVERVALUED`.

---

## 5. Error Cases Verified (by MockMvc tests)

| Case | Expected HTTP |
|---|---|
| Symbol not in DB | 404 |
| No fundamental snapshot exists | 422 |
| All models ineligible | 422 |
| Missing `wacc` in request | 400 |

---

## 6. Persistence Check

`ValuationResult` is written to the DB on each call. Verify via:

```sql
SELECT symbol, valuation_date, composite_fair_value, margin_of_safety, recommendation
FROM valuation_result vr
JOIN security s ON s.id = vr.security_id
WHERE s.symbol = 'AAPL'
ORDER BY valuation_date DESC
LIMIT 1;
```

Row must exist with `composite_fair_value` and `margin_of_safety` populated.

---

## 7. Weight Transparency in Response

The JSON response must include an `weights` object showing the effective (normalized) weights used.
Verify the `weights.dcf + weights.graham + weights.ddm ≈ 1.0` (sum to 1 within rounding).

---

## 8. MiFID II Disclaimer

`response.disclaimer` must equal exactly:

```
This is a decision-support tool, not investment advice (MiFID II).
```

---

## Merge Checklist

- [ ] `ValuationServiceTest` — all paths green
- [ ] `ValuationControllerTest` — green
- [ ] Reference value assertion passes
- [ ] `ValuationResult` persisted to DB (SQL check above or verified via test)
- [ ] `disclaimer` field present in response
- [ ] `weights` map present and sums to 1.0
- [ ] No new Checkstyle/compiler warnings
- [ ] Branch rebased on `main` with no conflicts
