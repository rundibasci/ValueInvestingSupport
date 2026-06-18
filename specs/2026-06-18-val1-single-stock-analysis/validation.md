# Validation — Phase Val1: Single-Stock Analysis Endpoint

## Definition of Done

Phase Val1 is complete and ready to merge when all of the following pass.

---

## 1. Integration Tests Green

```bash
./mvnw test -pl backend -Dtest="QuickAnalysisIT"
```

All tests in `QuickAnalysisIT` must pass. Expected minimum: **5 tests**.

---

## 2. Happy Path Response Shape

The integration test must assert the following fields are present and non-null on a 200 response:

| Field | Constraint |
|---|---|
| `symbol` | `"AAPL"` |
| `companyName` | non-blank |
| `currentPrice` | non-null (from seeded `PriceQuote`) |
| `valuation.composite` | non-null |
| `marginOfSafety` | non-null |
| `recommendation` | one of `STRONG_BUY`, `QUALITY_VALUE`, `FAIR_VALUE`, `OVERVALUED` |
| `disclaimer` | `"This is a decision-support tool, not investment advice (MiFID II)."` |
| `dataAsOf` | non-null |
| `source` | `"fmp"` |

---

## 3. Error Cases Verified (by integration tests)

| Case | Expected HTTP | Assertion |
|---|---|---|
| Unknown symbol (`ZZZZ`) | 404 | Response body contains `"Symbol not found"` |
| Stale snapshot (> 7 days old) | 422 | Response body contains `"stale"` |
| No auth token | 401 | No body assertion needed |

---

## 4. MiFID II Disclaimer Exact Match

The integration test must assert the full string exactly:

```
This is a decision-support tool, not investment advice (MiFID II).
```

A substring match is not acceptable — the full disclaimer must appear verbatim.

---

## 5. RULE-06 Guard Respected

When the seeded `FundamentalSnapshot` has fewer than 3 years of positive FCF (simulate by setting
FCF values to negative in a dedicated `@Sql` fixture), the response must have:
- `valuation.dcf = null`
- `valuation.composite` non-null (Graham-only fallback)
- `recommendation` non-null

This can be a separate test method in `QuickAnalysisIT` with an inline `@Sql` override.

---

## 6. Stale Data Guard Boundary

Two boundary tests in `QuickAnalysisIT`:

| Snapshot age | Expected result |
|---|---|
| 6 days old | 200 OK |
| 8 days old | 422 Unprocessable Entity |

---

## 7. Manual Smoke Test (optional for merge, required before Val2)

With the application running on the `demo` profile (H2 + Docker Redis, Flyway demo seed applied):

```bash
# 1. Login
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

# 2. Quick analysis
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/securities/AAPL/quick-analysis | jq .
```

**Expected:** HTTP 200, `compositeFairValue` non-null, `recommendation` set, `disclaimer` present,
`source = "fmp"`, `dataAsOf` matching the Flyway seed date.

---

## Merge Checklist

- [ ] `QuickAnalysisIT` — all 5+ tests green
- [ ] Happy path: all required fields present and non-null
- [ ] 404 on unknown symbol
- [ ] 422 on stale snapshot (> 7 days)
- [ ] 401 with no auth token
- [ ] RULE-06 guard: DCF null, Graham-only composite returned
- [ ] `disclaimer` field exact match
- [ ] `source = "fmp"` in response
- [ ] Flyway demo migration runs cleanly on `demo` profile startup
- [ ] No new compiler warnings
- [ ] Branch rebased on `main` with no conflicts
