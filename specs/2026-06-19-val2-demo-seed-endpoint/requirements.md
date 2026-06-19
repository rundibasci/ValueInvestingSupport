# Requirements — Val2: Demo Seed Endpoint & Smoke Test

## Scope

Implement an admin-only on-demand seed endpoint that triggers FMP ingestion for a configurable ticker list, runs the valuation pipeline for each ticker, and persists results. Completes the M3.5 Connected Demo milestone by making the full data → valuation path exercisable with a single HTTP call, and provides a shareable stakeholder script.

## Roadmap Reference

Group Val — Phase Val2 (follows Val1: Single-Stock Analysis Endpoint)

## Endpoint

### POST /api/v1/admin/seed

- **Auth:** ADMIN role required
- **Query param:** `tickers` (optional) — comma-separated list; defaults to `SEED_TICKERS` env var, then `AAPL,MSFT,KO,JNJ`
- **Pipeline per ticker (in order):**
  1. Fetch company profile via `MarketDataClient.getProfile(symbol)` → persist/update `Security`
  2. Fetch fundamentals → persist `FundamentalSnapshot`
  3. Fetch ratios → persist `RatioSnapshot`
  4. Fetch current price → persist `PriceQuote`, warm Redis cache
  5. Call `ValuationService.calculate()` → persist `ValuationResult`
- **Response body** — array, one entry per seeded ticker:
  ```json
  [
    {
      "symbol": "AAPL",
      "companyName": "Apple Inc.",
      "compositeFairValue": 210.5,
      "marginOfSafety": 13.6,
      "recommendation": "QUALITY_VALUE"
    }
  ]
  ```
- **Error handling:**
  - If FMP returns 404 for a ticker: skip it, include `{ "symbol": "XYZ", "error": "not found" }` in response array
  - If FMP is unavailable: 503 with descriptive message
  - Tickers that fail valuation (e.g. RULE-06 guard) still appear in the response with `compositeFairValue` from the fallback (Graham-only composite)

## Environment Variable

`SEED_TICKERS` — comma-separated default ticker list. Added to `.env.example`. When the `tickers` query param is present it takes precedence.

## Integration Test — ValuationDemoIT

- Profile: `@ActiveProfiles({"test", "fmpkey"})` — live FMP calls, real DB via Testcontainers PostgreSQL
- Flow:
  1. Login as admin → obtain JWT
  2. `POST /api/v1/admin/seed?tickers=AAPL`
  3. Assert response contains `marginOfSafety` non-null and `recommendation` set
  4. `GET /api/v1/securities/AAPL/quick-analysis`
  5. Assert `marginOfSafety` matches seed response and `dataAsOf` is today
- Key file: `backend/src/test/resources/application-fmpkey.yml` (gitignored, never committed)

## Demo Script — scripts/demo.sh

- Accepts optional `BASE_URL` as first argument; defaults to `http://localhost:8080`
- Documented curl sequence:
  1. `POST /auth/login` → capture JWT
  2. `POST /api/v1/admin/seed?tickers=AAPL,KO` → print seeded results
  3. `GET /api/v1/securities/AAPL/quick-analysis` → print analysis
  4. `POST /auth/logout` → clean up
- Prints each step as a labelled section; exits non-zero on HTTP error
- Shareable with stakeholders as a standalone validation script

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Seed pipeline depth | Full: ingest + valuate + persist | Matches spec; proves the end-to-end path works in one call |
| Integration test approach | FMP live via `fmpkey` profile | Consistent with established pattern (tech-stack.md); avoids mock/prod divergence risk |
| Demo script BASE_URL | Argument with localhost default | Works locally and against any deployed environment |
| Error response shape | Per-ticker `error` field in array | Client can see which tickers succeeded and which failed without parsing HTTP status alone |

## Out of Scope

- Bulk seeding of entire exchange (that is B3)
- Scheduling or recurring seed (that is B3)
- Returning detailed per-step ingestion logs (keep response shape simple)
- Score computation (that is Score1)
