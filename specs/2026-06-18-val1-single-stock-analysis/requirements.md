# Requirements — Phase Val1: Single-Stock Analysis Endpoint

## Scope

Implement `GET /api/v1/securities/{symbol}/quick-analysis` — the first production-quality
authenticated endpoint that wires DB-backed fundamentals → `ValuationService` → composite
fair value + MoS + recommendation into a single response.

**In scope:**
- `QuickAnalysisController` with the `GET` endpoint (auth required, all roles)
- `QuickAnalysisService` (or inline logic in controller if trivial) reading from DB
- Stale data guard: 422 if latest `FundamentalSnapshot` is older than 7 days
- Redis cache lookup for current price with fallback to most recent `PriceQuote` in DB
- `QuickAnalysisResponse` DTO (mirrors M0 schema + `dataAsOf`, `source`)
- MiFID II disclaimer in every response
- Flyway demo-profile migration (`V_demo_1__seed_aapl.sql`) with hardcoded AAPL data for local manual testing
- Integration test `QuickAnalysisIT`: H2 + `@Sql` seed + mocked `ValuationService` / `MarketDataClient`
- RULE-06 guard respected (DCF excluded if < 3 years positive FCF; falls back to Graham-only composite via `ValuationService`)

**Out of scope:**
- Val2 admin seed endpoint (`POST /api/v1/admin/seed`) — that is the next phase
- Writing new FMP ingestion logic — Val1 reads only from DB
- `GET /api/v1/securities/{symbol}/valuation` — that is E3
- B3 nightly jobs — they do not exist yet; data is seeded manually for this phase

---

## Decisions

| Decision | Answer | Rationale |
|---|---|---|
| Data source (B3 absent) | Flyway demo-profile migration + `@Sql` test fixtures | Lets the endpoint work against DB as designed without waiting for B3; no architectural compromise |
| DCF default parameters | Configurable in `application.yml` under `valuation.defaults.*` | Quick-analysis is opinionated; no user-supplied params; defaults apply conservative assumptions |
| Stale data threshold | 7 days | Defined in roadmap for Val1; prevents serving outdated fundamentals to users |
| Price source priority | Redis → `PriceQuote` DB | Mirrors roadmap; Redis is fast path, DB is fallback when cache cold |
| Validation bar | Integration test (H2 + mock client) | User preference; closer to target architecture than MockMvc-only |

---

## Default Valuation Parameters

Stored in `application.yml` under `valuation.defaults`:

```yaml
valuation:
  defaults:
    wacc: 0.09
    growth-y1-y5: 0.08
    growth-y6-y10: 0.04
    terminal-rate: 0.025
```

`requiredReturn` and `dividendGrowthRate` are left `null` → DDM is skipped in quick-analysis
(consistent with conservative default behaviour; DDM requires user input to be meaningful).

---

## Endpoint

```
GET /api/v1/securities/{symbol}/quick-analysis
```

- Auth required (all roles: `ADMIN`, `ADVISOR`, `INVESTOR`)
- Path variable `{symbol}` is case-insensitive
- No request body or query parameters

---

## Response Body

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "currentPrice": 182.50,
  "currency": "USD",
  "sector": "Technology",
  "financialSummary": {
    "revenue": 394330000000,
    "netIncome": 96995000000,
    "fcf": 111443000000,
    "eps": 6.13
  },
  "valuation": {
    "dcf": { "fairValue": 210.50, "low": 185.00, "high": 230.00 },
    "grahamNumber": 148.32,
    "composite": 192.10
  },
  "marginOfSafety": 5.27,
  "recommendation": "FAIR_VALUE",
  "disclaimer": "This is a decision-support tool, not investment advice (MiFID II).",
  "dataAsOf": "2026-06-01",
  "source": "fmp"
}
```

- `dataAsOf` — `reportDate` of the `FundamentalSnapshot` used
- `source` — always `"fmp"` for this endpoint (production data layer)
- `valuation.dcf` is `null` if RULE-06 guard applies (< 3 years positive FCF)
- `grahamNumber` is `null` if `eps ≤ 0` or `bvps ≤ 0`
- `currentPrice` and `marginOfSafety` may be `null` if no price data exists
- `recommendation` may be `null` if `marginOfSafety` is null

---

## Error Cases

| Condition | HTTP status | Detail message |
|---|---|---|
| Symbol not in `security` table | 404 | `"Symbol not found: {symbol}"` |
| Latest `FundamentalSnapshot` older than 7 days | 422 | `"Fundamental data for {symbol} is stale (as of {date}). Refresh required."` |
| No `FundamentalSnapshot` exists at all | 422 | `"No fundamental data available for: {symbol}"` |
| All valuation models ineligible (composite null) | 422 | `"No valuation model applicable for: {symbol}"` |

---

## Seed Strategy (B3 Absent)

### Test fixture (`@Sql`)

Each integration test class annotates with:
```java
@Sql(scripts = "/sql/seed_aapl_snapshot.sql", executionPhase = BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = AFTER_TEST_CLASS)
```

`seed_aapl_snapshot.sql` inserts into `security`, `fundamental_snapshot`, `ratio_snapshot`, and
`price_quote` with a `report_date` of `CURRENT_DATE - 1` (always fresh relative to the 7-day guard).

### Manual / demo profile

`V_demo_1__seed_aapl.sql` (Flyway, runs only on `demo` profile via `application-demo.yml`:
`spring.flyway.locations: classpath:db/migration,classpath:db/demo`). Inserts the same AAPL
row with a fixed date. Gitignored values (API key, real data) not needed — figures are plausible
but synthetic.

---

## Context & Dependencies

| Dependency | Status | Notes |
|---|---|---|
| `ValuationService.calculate(symbol, params)` | Done (C3) | Val1 calls it with default params |
| `ValuationResult` entity + repo | Done (A2, C3) | Val1 reads the persisted result |
| `FundamentalSnapshot` entity + repo | Done (A2) | Read by Val1 for `dataAsOf` and stale check |
| `RatioSnapshot` entity + repo | Done (A2) | Read for `financialSummary` fields |
| `PriceQuote` entity + repo | Done (A2) | Price fallback when Redis cache cold |
| Redis `@Cacheable` wrapping | Done (B2) | Val1 uses it for price lookup |
| JWT auth filter | Done (A3) | Val1 requires a valid token |
| `Recommendation` enum | Done (A2/C3) | Reused in response |
| Val2 seed endpoint | Not yet built | Will populate DB in production; Val1 uses Flyway seed instead |
