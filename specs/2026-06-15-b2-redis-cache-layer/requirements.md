# Requirements — Phase B2: Redis Cache Layer

## Context

Phase B1 delivered `MarketDataClient` with two implementations (`YahooMarketDataClient`, `FmpMarketDataClient`) selected via `MARKET_DATA_SOURCE`. B2 adds a Redis-backed caching layer in front of all client calls so that:
- downstream services (Valuation Engine, API endpoints) never pay the external API cost for repeated lookups within the TTL window;
- the system degrades gracefully if FMP is temporarily unavailable (cached data is served).

This phase depends on B1 completing successfully. It does not modify the Valuation Engine or the Score Engine.

## Scope

### In scope
- Spring Cache (`@Cacheable`, `@CacheEvict`) wrappers on every `MarketDataClient` method
- Redis as the cache store via Spring Data Redis (Lettuce driver)
- TTL configuration per data type (see below)
- Cache key strategy per roadmap: `mdc:{source}:{endpoint}:{symbol}:{params_hash}`
- Manual eviction endpoint (admin-only): `DELETE /api/v1/admin/cache/{symbol}` — evicts all cache entries for a given symbol across all endpoints
- Unit tests verifying `@Cacheable` behaviour (mocked Redis / in-memory cache manager)
- Integration test verifying live Redis hit/miss and TTL

### Out of scope
- Cache warming on startup
- Cache metrics endpoint or Prometheus integration (Phase I2)
- Frontend changes
- Any new FMP or Yahoo Finance endpoints

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | Use Spring Cache abstraction (`@Cacheable`) rather than manual `RedisTemplate` calls | Keeps cache logic declarative; allows swapping the store (e.g. to Caffeine for tests) without touching business code |
| D2 | TTL per data type configured in `application.yml`, not hardcoded | Makes TTLs adjustable per environment without recompile |
| D3 | Cache key includes `{source}` (yahoo/fmp) | Prevents key collisions if `MARKET_DATA_SOURCE` is changed at runtime or in tests |
| D4 | Manual eviction targets a whole symbol (all endpoints) | Simpler UX for operators who want to force-refresh a ticker after a corporate event |
| D5 | Use Lettuce (default Spring Boot Redis client) | No additional dependency; async, connection-pooled |

## TTL Configuration

| Data type | Cache name | TTL |
|---|---|---|
| Current price quote | `mdc-quote` | 15 minutes |
| Ratios (TTM) | `mdc-ratios` | 6 hours |
| Fundamentals (income, balance, cash flow) | `mdc-fundamentals` | 6 hours |
| Company profile | `mdc-profile` | 24 hours |

## Cache Key Strategy

```
mdc:{source}:{endpoint}:{symbol}
```

`{source}` = value of `MARKET_DATA_SOURCE` env var (`yahoo` or `fmp`)  
`{endpoint}` = short name matching the `MarketDataClient` method (`quote`, `profile`, `fundamentals`, `ratios`)  
`{symbol}` = ticker, uppercased

Example: `mdc:fmp:quote:AAPL`

## Environment Variables (additions)

```
REDIS_HOST   Redis hostname (already in .env.example from A1; used here for real)
REDIS_PORT   Redis port (default 6379)
```

No new variables required; Redis connection was already declared in Phase A1 scaffold.
