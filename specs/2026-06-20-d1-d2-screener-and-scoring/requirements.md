# Requirements — Group D: Value Score Engine & Stock Screener API (M4)

## Scope

Expose the `ValueScoreService` introduced in Score1 as a public API endpoint, then build the full screener API that queries persisted scores and fundamentals from the local database. This is **M4** on the roadmap.

Two phases are in scope:

| Phase | Deliverable |
|---|---|
| **D1** | `GET /api/v1/securities/{symbol}/score` — returns persisted `ValueScore` for a symbol; re-computes on demand if absent |
| **D2** | `POST /api/v1/screener`, `GET /api/v1/screener/presets`, `GET /api/v1/screener/sectors`, `GET /api/v1/screener/exchanges` |

---

## Context

### What exists already

- `ValueScoreService.compute(symbol)` — full 5-factor formula implemented in Score1; persists `ValueScore`
- `ValueScore` entity + `ValueScoreRepository` — scaffolded in A2, used in Score1
- `ValuationResult`, `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote` — all persisted by the pipeline
- `Security` entity with `sector`, `exchange` fields populated by FMP profile ingestion
- Spring Security auth filter — `ADMIN` and `ADVISOR`/`INVESTOR` roles in place
- JPA + Flyway + PostgreSQL stack fully running

### What this phase introduces

- `ScoreController` — thin REST layer over the existing `ValueScoreService`
- `ScreenerRequest` / `ScreenerResponse` DTOs
- `SecuritySpecification` — JPA Specification (Criteria API) builder for dynamic filters
- `ScreenerService` — applies `SecuritySpecification` to a `Pageable` query on `Security` joined to `ValueScore`, `RatioSnapshot`, `ValuationResult`
- `ScreenerController` — four endpoints
- Three preset definitions (Graham, Dividend, Quality)
- Testcontainers PostgreSQL integration test seeding 5 000+ rows to validate < 500 ms target

---

## Decisions

### D1: Score endpoint behaviour

The endpoint first looks up the most recent `ValueScore` for the symbol in DB. If no record exists (symbol ingested but not yet scored), it calls `ValueScoreService.compute(symbol)` inline and returns the freshly persisted result. If the symbol is not in DB at all, it returns 404.

No cache TTL is added in this phase; caching `ValueScore` in Redis belongs to a later hardening pass.

### D2: Query engine — JPA Specification / Criteria API

`SecuritySpecification` builds a compound `Predicate` from the non-null fields of `ScreenerRequest`. Each filter is an independent predicate; null fields are skipped. This pattern avoids hand-written dynamic JPQL and keeps each filter unit-testable in isolation.

The root query joins `Security` → `ValueScore` (most recent per symbol, using a subquery on `scoreDate`) and optionally `RatioSnapshot` (for D/E, ROIC filters).

### D2: Pagination & sorting

Caller supplies `page` (0-indexed) and `pageSize` (default 20, max 100). Sort field defaults to `totalScore DESC`. Any field present in `ScreenerResponse` can be used as a sort key; unknown sort fields fall back to default.

### D2: Query scope — DB only

The screener **never** calls `MarketDataClient` or FMP. It reads only from the local `security`, `value_score`, `ratio_snapshot`, and `valuation_result` tables. Symbols not yet ingested simply do not appear in results.

### D2: Performance target

`< 500 ms` p95 for a typical filter on a 5 000-row `security` table with `value_score` joined. Required indexes are added in a Flyway migration:
- `CREATE INDEX idx_value_score_security_date ON value_score(security_id, score_date DESC)`
- `CREATE INDEX idx_security_sector ON security(sector)`
- `CREATE INDEX idx_security_exchange ON security(exchange)`

### Integration test approach

Testcontainers PostgreSQL (not H2) is required because:
1. The < 500 ms assertion is meaningless on H2 (no real indexes).
2. The subquery for "most recent score per symbol" uses standard SQL that H2 may not handle identically.

Profile: `test` + `fmpkey` is **not** required for the screener test — data is seeded directly into the DB, no live FMP calls needed. A dedicated `screener-test` Spring profile with its own Testcontainers datasource is used.

---

## ScreenerRequest filters

All fields are optional. Null means "no filter on this dimension."

| Field | Type | Description |
|---|---|---|
| `sector` | `String` | Exact match on `security.sector` |
| `exchange` | `String` | Exact match on `security.exchange` |
| `minMarginOfSafety` | `BigDecimal` | `valuation_result.margin_of_safety ≥ value` |
| `maxMarginOfSafety` | `BigDecimal` | `valuation_result.margin_of_safety ≤ value` |
| `minValueScore` | `BigDecimal` | `value_score.total_score ≥ value` |
| `minRoic` | `BigDecimal` | `ratio_snapshot.roic ≥ value` |
| `maxDebtToEquity` | `BigDecimal` | `ratio_snapshot.debt_to_equity ≤ value` |
| `minDividendYield` | `BigDecimal` | `ratio_snapshot.dividend_yield ≥ value` |
| `minRevenueGrowth` | `BigDecimal` | computed from `fundamental_snapshot` revenue YoY |
| `sortField` | `String` | Default: `totalScore` |
| `sortDirection` | `String` | `ASC` or `DESC`; default `DESC` |
| `page` | `int` | 0-indexed; default 0 |
| `pageSize` | `int` | Default 20; max 100 |

### Preset definitions

| Preset | Filters applied |
|---|---|
| **Graham** | `minMarginOfSafety=15`, `maxDebtToEquity=1.0`, `minRoic=10` |
| **Dividend** | `minDividendYield=2.0`, `minMarginOfSafety=5` |
| **Quality** | `minRoic=15`, `minValueScore=60`, `maxDebtToEquity=1.5` |

---

## ScreenerResponse shape

```json
{
  "results": [
    {
      "symbol": "KO",
      "companyName": "Coca-Cola Co.",
      "sector": "Consumer Staples",
      "exchange": "NYSE",
      "currentPrice": 62.10,
      "compositeFairValue": 73.80,
      "marginOfSafety": 18.4,
      "totalScore": 72.5,
      "mosScore": 20,
      "qualityScore": 25,
      "safetyScore": 14,
      "growthScore": 10,
      "dividendScore": 0,
      "recommendation": "QUALITY_VALUE",
      "scoreDate": "2026-06-20"
    }
  ],
  "page": 0,
  "pageSize": 20,
  "totalElements": 142,
  "totalPages": 8
}
```

---

## Out of Scope

- No Redis caching of score or screener results (future hardening pass)
- No `GET /api/v1/screener/presets/{name}` dynamic CRUD — presets are hardcoded server-side
- No analyst estimates or FMP screener delegation
- No frontend changes in this phase (PFD1 or Group H wire these up)
- No alert integration
