# Requirements — Group E: Security Detail API (M5)

## Scope

Expose all per-security detail endpoints that together comprise the Security Detail view. This is **M5** on the roadmap.

Three phases are in scope in one branch:

| Phase | Deliverable |
|---|---|
| **E1** | `GET /api/v1/securities/search?q=`, `GET /api/v1/securities/{symbol}`, `GET /api/v1/securities/{symbol}/financials`, `GET /api/v1/securities/{symbol}/ratios` |
| **E2** | `GET /api/v1/securities/{symbol}/dividends`, `GET /api/v1/securities/{symbol}/insiders`, `GET /api/v1/securities/{symbol}/growth`, `GET /api/v1/securities/{symbol}/peers` |
| **E3** | `GET /api/v1/securities/{symbol}/valuation` — all models + analyst estimates |

---

## Context

### What exists already

- `Security` entity with `symbol`, `name`, `sector`, `exchange`, `country`, `currency`, `marketCap`, `description`, `ceo`, `employees`, `website`, `ipoDate` — populated by B3 profile sync
- `FundamentalSnapshot` with annual, quarterly, and TTM records — populated by B3 fundamentals sync; `periodType` field distinguishes ANNUAL / QUARTERLY / TTM
- `RatioSnapshot` — populated by B3 ratios sync
- `PriceQuote` — refreshed every 15 min by B3 quote job
- `DividendRecord` — populated by B3 nightly dividend job
- `InsiderTrade` — populated by B3 hourly insider feed
- `ValuationResult` (with `dcfFairValue`, `dcfFairValueLow`, `dcfFairValueHigh`, `grahamNumber`, `ddmValue`, `compositeFairValue`, `marginOfSafety`, `recommendation`) — persisted by `ValuationService.calculate()`
- `ValueScore` — persisted by `ValueScoreService.compute()`
- Spring Security auth filter — ADMIN, ADVISOR, INVESTOR roles in place
- `ScreenerController` / `ScoreController` in `it.mazzoni.vis.screener` / `.scoring` — pattern to follow
- `MarketDataClient` — **not called in this phase**; all data read from local DB

### What this phase introduces

- 9 new REST endpoints across 6 controllers in package `it.mazzoni.vis.security`
- `DividendsService` — streak computation and dividend CAGR calculation
- `GrowthService` — CAGR computation for revenue, FCF, EPS at 3y / 5y / 10y
- `AnalystEstimate` JPA entity + `analyst_estimate` table (new entity; table starts empty in production — no B3 ingestion job for analyst data yet)
- `AnalystEstimateRepository`
- Flyway migrations: `V{N}__security_detail_indexes.sql` + `V{N+1}__analyst_estimate.sql`
- Testcontainers PostgreSQL integration test `SecurityDetailIT`

---

## Decisions

### DB-first; no live FMP calls at request time

All Security Detail endpoints read exclusively from persisted DB data. No `MarketDataClient` call is made during a request. If data is absent or stale the endpoint returns 4xx — it does not trigger a live fetch.

Rationale: predictable latency, zero FMP quota burn on UI page loads, system stays available when FMP is down (Design Principle 5 — cache-first for external data).

### Stale data guard — 7-day threshold on profile and valuation

`GET /api/v1/securities/{symbol}` (profile) and `GET /api/v1/securities/{symbol}/valuation` return 422 if the most recent annual `FundamentalSnapshot` or `ValuationResult` respectively is older than 7 days. All other endpoints (ratios history, dividends, insiders, growth) are exempt — their historical records are valid regardless of age.

### Search — substring LIKE, no full-text index

`GET /api/v1/securities/search?q=` performs case-insensitive `LIKE %q%` on both `symbol` and `name`, limited to 10 results. No Elasticsearch or `tsvector` needed at ≤ 10 000 securities. A new `idx_security_name` index covers the name column.

### Peers — same-sector, closest market cap, DB only

Peers are up to 5 securities in the same `sector` with the closest `marketCap`. Computed entirely from the local `security` table — no dedicated peers table, no FMP call. If fewer than 5 peers exist in the sector, return however many are available.

### Analyst estimates — new entity, table starts empty

`AnalystEstimate` is created in this phase. No B3 ingestion job populates it yet; in production the `analystEstimates` field in the valuation response will be `null` until a future phase adds analyst data ingestion. The integration test seeds 3 rows manually to validate the aggregation logic.

### Growth CAGR — require n+1 annual snapshots

CAGR at n years requires at least n+1 annual `FundamentalSnapshot` records. If fewer exist, that window returns `null` (not an error). For example, with only 4 annual records: `cagr3y` is computable, `cagr5y` and `cagr10y` are null.

### MoS range in valuation response

`mosLow` is computed from `ValuationResult.fairValueLow` (DCF pessimistic — WACC+2%) vs current price; `mosHigh` from `ValuationResult.fairValueHigh` (DCF optimistic — WACC-1%). This gives a range band alongside the point-estimate `marginOfSafety`.

---

## Request / Response Shapes

### `GET /api/v1/securities/search?q={q}`

```json
[
  { "symbol": "AAPL", "companyName": "Apple Inc.", "sector": "Technology", "exchange": "NASDAQ" }
]
```
Max 10 items. Empty array when `q` is blank.

---

### `GET /api/v1/securities/{symbol}`

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "sector": "Technology",
  "exchange": "NASDAQ",
  "country": "US",
  "currency": "USD",
  "marketCap": 2750000000000,
  "description": "Apple Inc. designs, manufactures...",
  "ceo": "Tim Cook",
  "employees": 164000,
  "website": "https://www.apple.com",
  "ipoDate": "1980-12-12",
  "currentPrice": 182.50,
  "priceDate": "2026-06-20",
  "revenue": 383285000000,
  "netIncome": 96995000000,
  "fcf": 99584000000,
  "eps": 6.43,
  "bvps": 4.25,
  "pe": 28.4,
  "roic": 26.4,
  "dividendYield": 0.56,
  "dataAsOf": "2026-06-19"
}
```

---

### `GET /api/v1/securities/{symbol}/financials`

```json
{
  "symbol": "AAPL",
  "annuals": [
    { "fiscalYear": 2025, "revenue": 383285000000, "netIncome": 96995000000, "fcf": 99584000000, "eps": 6.43, "bvps": 4.25 }
  ],
  "quarters": [
    { "period": "Q1-2026", "revenue": 124300000000, "netIncome": 36330000000, "fcf": 27840000000, "eps": 2.40 }
  ],
  "ttm": { "revenue": 395000000000, "netIncome": 100000000000, "fcf": 102000000000, "eps": 6.70 }
}
```
`annuals`: up to 10 entries ordered newest-first. `quarters`: up to 8 entries ordered newest-first. `ttm`: single record or null if not ingested.

---

### `GET /api/v1/securities/{symbol}/ratios`

```json
{
  "symbol": "AAPL",
  "ratios": [
    {
      "date": "2025-09-30",
      "pe": 28.4,
      "roic": 26.4,
      "roe": 147.3,
      "debtToEquity": 1.87,
      "grossMargin": 46.2,
      "fcfMargin": 26.0,
      "dividendYield": 0.56
    }
  ]
}
```
Up to 10 entries ordered newest-first.

---

### `GET /api/v1/securities/{symbol}/dividends`

```json
{
  "symbol": "AAPL",
  "history": [
    { "paymentDate": "2025-11-14", "amount": 0.25, "currency": "USD" }
  ],
  "streak": 12,
  "cagr3y": 5.2,
  "cagr5y": 7.1,
  "cagr10y": 9.8
}
```
`cagr*` values are null when insufficient history. `streak` is 0 when no dividends recorded.

---

### `GET /api/v1/securities/{symbol}/insiders`

```json
{
  "symbol": "AAPL",
  "trades": [
    {
      "transactionDate": "2026-05-15",
      "name": "Tim Cook",
      "title": "CEO",
      "transactionType": "SALE",
      "shares": 200000,
      "pricePerShare": 185.20,
      "totalValue": 37040000
    }
  ]
}
```
Last 12 months only; ordered newest-first.

---

### `GET /api/v1/securities/{symbol}/growth`

```json
{
  "symbol": "AAPL",
  "revenue": { "cagr3y": 6.1, "cagr5y": 8.2, "cagr10y": 12.3 },
  "fcf":     { "cagr3y": 4.8, "cagr5y": 7.0, "cagr10y": 10.5 },
  "eps":     { "cagr3y": 9.1, "cagr5y": 11.2, "cagr10y": 14.0 }
}
```
Any window with insufficient history returns `null` for that CAGR field.

---

### `GET /api/v1/securities/{symbol}/peers`

```json
{
  "symbol": "AAPL",
  "peers": [
    {
      "symbol": "MSFT",
      "companyName": "Microsoft Corp.",
      "currentPrice": 425.10,
      "compositeFairValue": 390.0,
      "marginOfSafety": -8.2,
      "totalScore": 68.4,
      "pe": 35.2,
      "roic": 31.0
    }
  ]
}
```
Up to 5 peers. Empty array if no other securities in the same sector.

---

### `GET /api/v1/securities/{symbol}/valuation`

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "currentPrice": 182.50,
  "dcf": {
    "base": 210.5,
    "low": 180.0,
    "high": 235.0,
    "enterpriseValue": 2350000000000
  },
  "grahamNumber": 9.51,
  "ddmValue": null,
  "fmpDcfValue": 205.0,
  "compositeFairValue": 155.0,
  "marginOfSafety": 13.6,
  "mosLow": -1.4,
  "mosHigh": 22.3,
  "recommendation": "QUALITY_VALUE",
  "analystEstimates": {
    "priceTargetMean": 203.33,
    "priceTargetLow": 190.0,
    "priceTargetHigh": 220.0,
    "analystCount": 3,
    "consensus": "BUY"
  },
  "dataAsOf": "2026-06-19",
  "disclaimer": "This is a decision-support tool, not investment advice (MiFID II)."
}
```
`ddmValue` is null when RULE-07 guard blocks DDM (< 5 consecutive dividend years). `fmpDcfValue` is null if the field is absent in `ValuationResult`. `analystEstimates` is null when no rows exist in `analyst_estimate` for the symbol.

---

## Authorization

All 9 endpoints require authentication. Any role (ADMIN, ADVISOR, INVESTOR) is accepted. No endpoint is ADMIN-only.

| Endpoint | Required role |
|---|---|
| All `GET /api/v1/securities/**` | `hasAnyRole("ADMIN","ADVISOR","INVESTOR")` |

---

## Out of Scope

- No analyst data ingestion job — `analyst_estimate` table created but left empty in production until a future phase
- No write endpoints on any security — all Security Detail endpoints are read-only
- No user-specific flags (e.g. "in my watchlist") — belongs to Group F
- No real-time price streaming — current price from most recent `PriceQuote` in DB
- No full-text search index — LIKE query sufficient at ≤ 10 000 securities
- No FMP live call for `fmpDcfValue` — reads from `ValuationResult` field if present; null otherwise
- No Redis caching of Security Detail responses — future hardening pass
- No frontend changes in this phase — PFD1 and Group H wire these up
