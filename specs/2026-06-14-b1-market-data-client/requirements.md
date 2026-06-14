# Requirements — B1: Market Data Client Abstraction

## Scope

Define the `MarketDataClient` interface and implement both concrete providers — Yahoo Finance (reusing Z2) and FMP (new) — behind a single abstraction. The active implementation is selected at startup via the `MARKET_DATA_SOURCE` environment variable. All methods return transient domain records; nothing is persisted to DB in this phase.

B1 is the prerequisite for B2 (Redis cache layer) and B3 (ingestion jobs). Both of those phases will call `MarketDataClient` without knowing which provider is active.

## What's in scope

- `MarketDataClient` interface: `getProfile(symbol)`, `getFundamentals(symbol)`, `getRatios(symbol)`, `getQuote(symbol)`
- `YahooMarketDataClient` — wraps existing Z2 `YahooFinanceClient` and `FundamentalAdapter`; active when `MARKET_DATA_SOURCE=yahoo`
- `FmpMarketDataClient` — Spring WebClient with `apikey` header, exponential backoff retry (max 3), explicit handling of 429 / 503; active when `MARKET_DATA_SOURCE=fmp`
- `MarketDataException` — typed exception for NOT_FOUND and SERVICE_UNAVAILABLE conditions
- WireMock tests for both implementations (no live network calls in CI)

## What's out of scope

- Redis caching (B2)
- DB persistence of fetched snapshots (B3)
- Circuit breaker / Resilience4j (not specified for B1)
- Bulk / batch endpoints (B3 concern)

## Decisions

| Decision | Choice | Reason |
|---|---|---|
| Abstraction mechanism | `@ConditionalOnProperty(name = "market-data.source", havingValue = "yahoo\|fmp")` | Clean Spring idiom; only one bean exists in the context at runtime |
| Return contract | Transient domain objects (`CompanyProfile`, `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote`) | Decouples fetch from persistence; B3 ingestion jobs own the save() calls |
| FMP retry policy | Exponential backoff — initial 1 s, multiplier 2×, max 3 attempts; retries on 429 and 503 only | Respects FMP rate limits; hard 4xx errors (404, 401) fail immediately |
| Error taxonomy | `MarketDataException(ErrorCode.NOT_FOUND)` for 404; `MarketDataException(ErrorCode.SERVICE_UNAVAILABLE)` for 503 / retries exhausted | Callers (B3 jobs) can distinguish recoverable from permanent failures |
| FMP base URL | `https://financialmodelingprep.com/stable/` | Stable endpoint family per FMP docs and tech-stack.md |
| Test approach | WireMock stubs for both clients | Deterministic, offline, validates URL construction and retry logic |

## Key env vars (additions for B1)

```
MARKET_DATA_SOURCE    yahoo | fmp          (required; no default)
FMP_API_KEY           FMP Premium API key  (required when source=fmp)
```

Existing vars (`REDIS_HOST`, `REDIS_PORT`, `DATABASE_URL`) are inherited from A1 and unused by B1 itself (B2 wires the cache).

## Dependencies

- Z2: `YahooFinanceClient`, `FundamentalAdapter`, Yahoo DTOs — reused directly
- A2: `FundamentalSnapshot`, `RatioSnapshot`, `PriceQuote`, `Security` JPA entities — used as value objects (no `EntityManager` operations in B1)
