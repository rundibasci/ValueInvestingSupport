# Requirements — Phase Z4: Demo Analysis Endpoint

## Scope

Expose a single unauthenticated REST endpoint that accepts a ticker symbol, fetches market data via the existing Yahoo Finance client, runs the Valuation Engine, and returns a structured JSON response.

This is the final backend piece of the M0 demo milestone. No auth, no database persistence.

## Endpoint

```
GET /demo/analyze/{symbol}
```

- No authentication required (Spring Security must permit this path)
- Returns `200 OK` with the analysis JSON on success
- Returns `404 Not Found` if Yahoo Finance returns no data for the symbol
- Returns `503 Service Unavailable` (with message body) if Yahoo Finance is unreachable

## Response Shape

```json
{
  "symbol": "AAPL",
  "companyName": "Apple Inc.",
  "currentPrice": 182.5,
  "currency": "USD",
  "sector": "Technology",
  "financialSummary": {
    "revenue": 394328000000,
    "netIncome": 96995000000,
    "fcf": 99584000000,
    "eps": 6.13
  },
  "valuation": {
    "dcf": { "fairValue": 210.5, "low": 185.0, "high": 230.0 },
    "grahamNumber": 9.51,
    "composite": 155.0
  },
  "marginOfSafety": 13.6,
  "recommendation": "UNDERVALUED",
  "disclaimer": "This is a decision-support tool, not investment advice (MiFID II)."
}
```

## Composite Fair Value Rules

| Condition | Composite Calculation |
|---|---|
| DCF eligible (≥ 3 years positive FCF) | DCF × 60% + Graham × 40% |
| DCF skipped (< 3 years positive FCF) | Graham × 100% |

When DCF is skipped, `valuation.dcf` is `null` in the response.

## Recommendation Labels

| Margin of Safety | Label |
|---|---|
| > 15% | `QUALITY_VALUE` |
| 5% – 15% | `UNDERVALUED` |
| 0% – 5% | `FAIRLY_VALUED` |
| Negative | `OVERVALUED` |

## Layering

```
DemoAnalysisController
    └── DemoAnalysisService
            ├── YahooFinanceClient          (Z2)
            ├── YahooFinanceAdapter         (Z2)
            ├── DcfCalculator               (Z3)
            ├── GrahamCalculator            (Z3)
            └── MarginOfSafetyCalculator    (Z3)
```

`DemoAnalysisService` owns all orchestration: fetch → map → calculate → compose response. The controller only handles HTTP concerns (path variable extraction, exception-to-status mapping).

## Out of Scope

- Database persistence (ValuationResult not saved)
- Redis caching (Caffeine cache from Z2 applies to Yahoo calls; no additional caching needed)
- DDM (no reliable dividend data from Yahoo for demo)
- Authentication / authorization
- FMP data source
