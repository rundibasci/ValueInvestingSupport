# Validation — Group F3: Portfolio Builder (Simulation) (M6)

## Merge gates

### 1. Contract and authorization tests

```bash
mvn test -pl backend -Dtest="PortfolioControllerTest,PortfolioSimulationServiceTest"
```

- Valid `POST /api/v1/portfolios/{id}/simulate` returns `200`, proposal rows, aggregate values, excluded symbols, and the MiFID II disclaimer.
- Missing/non-positive budget and invalid percentages return `400`.
- No token returns `401`; a portfolio owned by another user returns `404`.
- An empty or entirely ineligible candidate set, or one unable to buy a single permitted share, returns `422`.

### 2. Allocation correctness tests

Using deterministic local fixtures, assert that:

- candidates are prioritized by normalized positive `totalScore`;
- no proposed row exceeds `maxStockPercent`, and sector/country aggregates never exceed their caps;
- a cap redistributes allocation only to still-eligible candidates in deterministic score/symbol order;
- every proposed share quantity is a non-negative integer and `investedAmount + unallocatedCash = budget` at currency precision;
- MoS and dividend-yield filters exclude matching symbols with stated reasons;
- no candidate missing required local quote, score, valuation, sector, or country is allocated;
- no holdings, watchlist entries, or valuation/score records change after simulation.

### 3. PostgreSQL integration test

```bash
mvn test -pl backend -Dtest=PortfolioSimulationIT
```

Seed a portfolio and watchlist with at least five symbols spanning two sectors and two countries, including one deliberately ineligible symbol. Then:

1. Authenticate the owner and call simulate with a 10,000 budget and defaults.
2. Verify a non-empty proposal, correct price-based whole-share calculations, and a positive residual cash no greater than the cheapest eligible share price when capacity remains.
3. Verify 25% per stock, 40% per sector, and 50% per country caps from the response aggregates.
4. Confirm the deliberately incomplete symbol is returned as excluded.
5. Fetch the portfolio before and after and prove its holdings are identical.
6. Repeat as a second user and expect `404`.

### 4. Regression suite

```bash
mvn test -pl backend
```

All existing authentication, watchlist, scoring, security-detail, and portfolio CRUD tests must remain green.

### 5. Manual smoke test

With seeded local data and a valid JWT:

```bash
curl -s -X POST "http://localhost:8080/api/v1/portfolios/$PORTFOLIO_ID/simulate" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"budget":10000,"maxStockPercent":25,"maxSectorPercent":40,"maxCountryPercent":50}' | jq .
```

Confirm proposal weights honour every cap, all share counts are whole numbers, excluded candidates explain why they were omitted, the disclaimer is present, and `GET /api/v1/portfolios/$PORTFOLIO_ID` shows no holdings changes.

## Ready to merge when

All checks above pass on the F3 branch; no test is disabled; the endpoint is read-only, ownership-safe, constraint compliant, and contains the required MiFID II decision-support disclaimer.
