# Validation — Phase Z4: Demo Analysis Endpoint

## Automated Tests (must pass before merge)

### Controller integration tests — `DemoAnalysisControllerTest`

| # | Test | Expected |
|---|---|---|
| T1 | Happy path (DCF eligible) | `200 OK`, all JSON fields present, `valuation.dcf` not null, `marginOfSafety` is a number, `recommendation` is one of the four labels |
| T2 | DCF skipped (< 3 years positive FCF) | `200 OK`, `valuation.dcf` is null, `valuation.composite` equals `grahamNumber` (100% Graham) |
| T3 | Symbol not found | `404 Not Found`, body contains `"error"` key |
| T4 | Yahoo Finance unavailable | `503 Service Unavailable`, body contains `"error"` key |

All four tests use `MockMvc` with `@MockBean YahooFinanceClient`. No real network calls.

## Manual Smoke Test (run once on local)

```bash
# Start the app with demo profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Happy path
curl -s http://localhost:8080/demo/analyze/AAPL | jq .

# Symbol that doesn't exist
curl -v http://localhost:8080/demo/analyze/FAKESYMBOL999
# Expect: HTTP 404
```

### Checklist for manual verification

- [ ] `symbol` matches the ticker passed in the URL
- [ ] `currentPrice` is a positive number
- [ ] `valuation.grahamNumber` is present and positive
- [ ] `valuation.composite` is present and positive
- [ ] `marginOfSafety` is a number (may be negative for overvalued stocks)
- [ ] `recommendation` is one of: `QUALITY_VALUE`, `UNDERVALUED`, `FAIRLY_VALUED`, `OVERVALUED`
- [ ] `disclaimer` contains "MiFID II"
- [ ] `FAKESYMBOL999` returns HTTP 404

## Definition of Done

Phase Z4 is mergeable when:

1. All four automated tests pass (`./mvnw test`)
2. Manual curl against AAPL returns a well-formed JSON response
3. `/demo/analyze/FAKESYMBOL999` returns 404
4. No auth header is required for any call to `/demo/**`
