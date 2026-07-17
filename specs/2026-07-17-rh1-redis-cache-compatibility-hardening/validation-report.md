# RH1 Redis Cache Compatibility Hardening — Validation Report

## Result

RH1 is implemented. The systemic Redis round-trip defect is fixed with typed cache families, coordinated schema namespaces, scoped recovery, and complete symbol eviction.

## Root Cause and Design

The seven failures were not symbol-specific. The raw Yahoo layer stored heterogeneous `QuoteSummaryResponse` and `ChartResponse` values in one `yahoo-finance` cache while the application-wide Redis configuration used a generic polymorphic serializer. A startup-populated value could therefore fail when read during the later asynchronous seed run.

The implementation keeps raw Yahoo caching because one quote-summary/chart response is reused by several normalized market-data operations, but separates it into two declared cache families:

| Cache | Value contract | Namespace/TTL owner |
|---|---|---|
| `yahoo-quote-summary` | `QuoteSummaryResponse` | `CacheSchema.YAHOO_VERSION`; fundamentals TTL |
| `yahoo-chart` | `ChartResponse` | `CacheSchema.YAHOO_VERSION`; quote TTL |
| `mdc-profile` | `CompanyProfile` | `CacheSchema.MARKET_DATA_VERSION`; profile TTL |
| `mdc-fundamentals` | `FundamentalSnapshot` | `CacheSchema.MARKET_DATA_VERSION`; fundamentals TTL |
| `mdc-ratios` | `RatioSnapshot` | `CacheSchema.MARKET_DATA_VERSION`; ratios TTL |
| `mdc-annual-ratios` | `List<RatioSnapshot>` via explicit Jackson `JavaType` | `CacheSchema.MARKET_DATA_VERSION`; ratios TTL |
| `mdc-quote` | `MarketPriceQuote` | `CacheSchema.MARKET_DATA_VERSION`; quote TTL |

Yahoo moved from `v2` to `v3`; normalized market data moved from `v10` to `v11`. Old values remain isolated until TTL expiry. No global Redis deletion is required.

## Delivered

- Replaced the heterogeneous `GenericJackson2JsonRedisSerializer` with typed JSON serializers per cache family.
- Centralized Yahoo and normalized market-data schema versions in `CacheSchema`.
- Added deterministic Yahoo key generation through `YahooCacheKeyHelper`.
- Added a Redis-only cache error handler that evicts one unreadable entry and treats the read as a miss.
- Kept connection, write, eviction, provider, database, and application failures fail-fast.
- Added low-cardinality recovery metric `vis.cache.read.recovered` and sanitized logging with a short key hash, never the raw key/value.
- Enabled synchronized cache loading at the owning market-data boundaries to coalesce concurrent misses/recovery per application instance; the seed worker remains bounded and sequential.
- Extended manual symbol eviction across both normalized and raw Yahoo cache layers.
- Added a real-Redis Testcontainers round-trip contract test for every production value family.
- Added unit coverage for scoped recovery, non-serialization propagation, write-error propagation, and full multi-layer eviction.

## Automated Verification

- Production compilation: passed.
- Focused recovery and eviction tests: passed.
- Real Redis Testcontainers contract (`RedisCacheContractIT`): passed.
- Cache, Yahoo, and seed-run regression selection: passed.
- Complete backend Maven test suite: passed.
- Host tests required elevated execution because Java 26 otherwise blocks Mockito/Byte Buddy self-attachment; the project runtime remains Java 21.

## Live Seven-Symbol Replay

Environment: real-demo PostgreSQL and Redis retained from the due-diligence run; backend rebuilt with RH1. This deliberately verified namespace coexistence rather than relying on `FLUSHDB`.

Symbols: `AAPL`, `JNJ`, `KO`, `PEP`, `PG`, `UNP`, `XOM`.

| Replay | Result |
|---|---|
| First RH1 seed with old Redis namespaces still present | 7/7 seeded; 0 Redis errors |
| Immediate warm-cache reseed | 7/7 seeded; 0 Redis errors |
| Backend-only restart, Redis retained, then reseed | 7/7 seeded; 0 Redis errors |
| Final Docker image with synchronized loading enabled | 7/7 seeded; 0 Redis errors |

No authentication/session keys were deliberately deleted during rollout or validation.

## Full-Universe Replay

The original 310-symbol universe was resubmitted as seed run `8a4863aa-96a7-4d11-ac5f-3b45d9f2bb02`.

- Terminal status: `PARTIAL_SUCCESS`.
- Processed: 310/310.
- Succeeded: 302.
- Failed: 8.
- Redis deserialization failures: **0** (previously 7).
- Backend log matches for deserialization or cache recovery during replay: **0**.
- Unrelated known failures: seven transaction rollback symbols (`ALB`, `APD`, `DOW`, `FMC`, `IP`, `MRNA`, `WBA`) and one symbol-normalization/not-found case (`BF.B`).

The success count improved from 295 to 302 solely by eliminating the seven Redis failures. The remaining eight outcomes match the explicitly out-of-scope defect categories and require separate phases.

## Final Assessment

RH1 meets its merge criteria. Cache contracts round-trip through Redis, the observed symbols pass cold/warm/post-restart workflows, the full universe contains zero Redis deserialization failures, no global flush was used, and the complete backend suite passes.
