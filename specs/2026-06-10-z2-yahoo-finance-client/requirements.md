# Requirements — Z2: Yahoo Finance Client

## Context

Phase Z2 implements the data ingestion layer for the demo milestone (M0). It provides the
`YahooFinanceClient` that fetches raw market data from Yahoo Finance's unofficial public API
(no key required) and adapts it into domain records (`FundamentalSnapshot`, `RatioSnapshot`)
consumed by the Valuation Engine (Z3).

Z2 must be fully self-contained: no PostgreSQL, no Redis, no auth. Caching is in-memory only
(Caffeine). The adapter interface it establishes is the same contract that Group B (Phase B1)
will extend when switching to FMP.

## Scope

### In Scope

- `YahooFinanceClient` — Spring WebClient calling two Yahoo Finance endpoints:
  - `GET https://query1.finance.yahoo.com/v10/finance/quoteSummary/{symbol}?modules=…`
    with all 7 modules: `financialData`, `defaultKeyStatistics`, `incomeStatementHistory`,
    `balanceSheetHistory`, `cashflowStatementHistory`, `summaryDetail`, `assetProfile`
  - `GET https://query1.finance.yahoo.com/v8/finance/chart/{symbol}` for current price
- Response DTOs as Java records mapping Yahoo Finance JSON structure
- In-memory Caffeine cache with hardcoded 15-min TTL (no Redis for demo milestone)
- `YahooFinanceAdapter` — maps Yahoo Finance DTOs → domain records:
  - `FundamentalSnapshot` (revenue, net income, FCF, EPS, BVPS, shares, net debt, current price)
  - `RatioSnapshot` (PE, ROIC, ROE, current ratio, debt/equity, dividend yield)
- Error handling: symbol not found → `SymbolNotFoundException`; API unavailable → `MarketDataUnavailableException`
- Unit tests with captured JSON fixtures (offline, hermetic)
- One optional `@Tag("integration")` live test against real Yahoo Finance (skipped in CI by default)

### Out of Scope

- Redis caching (Group B, Phase B2)
- FMP client (Group B, Phase B1)
- Database persistence of snapshots (Group A, Phase A2)
- Bulk/screener endpoints (Yahoo Finance does not provide these)

## Key Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | All 7 `quoteSummary` modules fetched in one call | Single HTTP round-trip; Yahoo Finance accepts comma-separated module list |
| D2 | Caffeine TTL hardcoded at 15 min | Matches roadmap; Redis replaces this in B2 with no adapter change |
| D3 | DTOs are Java records (immutable) | Aligns with Java 21 idiom; records prevent accidental mutation |
| D4 | Adapter maps to domain records, not JPA entities | Domain records are data-source agnostic; JPA entities are added in Group A |
| D5 | Null-safe mapping throughout | Yahoo Finance omits fields for some company types (e.g. no FCF for banks) |
| D6 | Offline JSON fixtures + optional live integration test | Keeps CI hermetic; live test gives confidence when Yahoo API format changes |

## Business Rules Applied

- **RULE-06**: DCF is skipped if fewer than 3 years of positive FCF are found in the mapped
  data. The adapter must surface the full FCF history (up to 4 years) so Z3 can apply this rule.
- **MiFID II**: No recommendation logic lives in this phase. The adapter is pure data mapping.

## Domain Records Produced

```
FundamentalSnapshot
  symbol, companyName, sector, currency, currentPrice
  revenue[], netIncome[], fcf[], eps[], sharesOutstanding
  bookValuePerShare, netDebt, reportingCurrency

RatioSnapshot
  symbol, peRatio, roic, roe, currentRatio, debtToEquity, dividendYield
```

## Dependencies

- Z1 completed (Spring Boot scaffold, `demo` profile, WebClient on classpath)
- No external services required at runtime for unit tests
