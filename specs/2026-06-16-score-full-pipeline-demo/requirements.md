# Requirements — Group Score: Full Pipeline Demo

## Context

Group Val (Val1 + Val2) delivered a stakeholder-showable single-stock analysis endpoint and the
`POST /api/v1/admin/seed` ingest trigger. The pipeline so far proves: auth → FMP ingest →
valuation → MoS for one ticker at a time.

Group Score extends this to the full value-investing chain: **Seed → Valuate → Score → Rank**.
A single admin endpoint accepts a list of tickers, runs the entire chain, and returns the tickers
ranked by their composite Value Score. This validates the scoring formula end-to-end and gives
stakeholders a ranked view before any screener UI or full screener API (Group D) exists.

`ValueScoreService` introduced here uses the exact same 5-factor formula that D1 will persist
and expose individually — no duplication at merge time; D1 just adds DB persistence and a
`GET /score` endpoint on top.

## Scope

### In scope

- `POST /api/v1/admin/pipeline-run` (ADMIN only): accepts `{ "tickers": [...] }`, runs full
  chain per ticker, returns ranked array
- `ValueScoreService.compute(symbol, ValuationResult, RatioSnapshot) → ValueScore`: full 5-factor
  formula (MoS 30, Quality 25, Safety 20, Growth 15, Dividend 10)
- Persist `ValueScore` to DB (same entity D1 will query)
- Integration test `PipelineDemoIT`: login → pipeline-run → assert `totalScore` non-null,
  `marginOfSafety` non-null, tickers sorted by score DESC
- `scripts/pipeline-demo.sh`: login → pipeline-run → print ranked table (shareable with stakeholders)

### Out of scope

- `GET /api/v1/securities/{symbol}/score` endpoint (D1)
- `POST /api/v1/screener` (D2)
- Full screener filter API
- Frontend changes
- B3 nightly ingestion jobs (jobs are not triggered; data is seeded on demand via this endpoint)

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | `ValueScoreService` accepts `ValuationResult` + `RatioSnapshot` as inputs (not symbol string) | Makes the service unit-testable without DB; D1 will add a `compute(symbol)` convenience wrapper that resolves inputs from DB |
| D2 | Pipeline endpoint reuses `MarketDataClient` → `ValuationService` chain from Val2 seed | No duplication; existing seed logic is called internally |
| D3 | `ValueScore` persisted to DB after each compute | D1's `GET /score` endpoint can read it immediately without re-computing |
| D4 | Response sorted by `totalScore DESC` | Demonstrates ranking concept before screener UI exists; stakeholders see which ticker is most undervalued |
| D5 | Integration test uses `localstack` profile (H2 + Docker Redis) matching LS1 | Consistent with existing IT infrastructure; no Testcontainers complexity |

## Value Score Formula

Five sub-scores, weights configurable but defaulting to:

| Sub-score | Weight | Input fields |
|---|---|---|
| MoS Score | 30 | `marginOfSafety` from `ValuationResult` |
| Quality Score | 25 | `roic`, `roe`, `grossMargin` from `RatioSnapshot` |
| Safety Score | 20 | `debtToEquity`, `currentRatio` from `RatioSnapshot` |
| Growth Score | 15 | revenue and EPS CAGR proxied from `FundamentalSnapshot` history |
| Dividend Score | 10 | `dividendYield`, `payoutRatio` from `RatioSnapshot` |

`totalScore = Σ(subScore × weight)` — capped at 100.

Sub-scores are normalized to 0–100 before weighting. Null inputs yield 0 for that sub-score
(not a fatal error).

## Pipeline Endpoint Contract

```
POST /api/v1/admin/pipeline-run
Authorization: Bearer <admin-token>
Content-Type: application/json

{ "tickers": ["AAPL", "MSFT", "KO", "JNJ"] }
```

Response `200 OK`:
```json
[
  {
    "symbol": "KO",
    "companyName": "Coca-Cola Co.",
    "compositeFairValue": 58.20,
    "currentPrice": 61.50,
    "marginOfSafety": -5.4,
    "totalScore": 72.5,
    "recommendation": "FAIR_VALUE"
  },
  ...
]
```

Sorted by `totalScore DESC`. Error cases:
- `400` if `tickers` list is empty or null
- Per-ticker failures (FMP unavailable, symbol not found) skip that ticker and add it to an
  `errors` array in the response — partial success is acceptable

## Environment Variables

No new variables. Requires `FMP_API_KEY` (already declared) and Redis running on `REDIS_HOST:REDIS_PORT`.
