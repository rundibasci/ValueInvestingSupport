# RH1 Redis Cache Compatibility Hardening — Requirements

## Purpose

Eliminate the systemic Redis deserialization failure reproduced during the 2026-07-17 operational due diligence on `AAPL`, `JNJ`, `KO`, `PEP`, `PG`, `UNP`, and `XOM`. These are seven manifestations of one cache-contract defect, not seven symbol-specific defects.

The fix must make cached market-data values readable after a warm-cache hit, survive compatible deployments, safely bypass incompatible entries, and preserve authentication/session data stored in the same Redis database.

## Evidence and Context

- Seed run `f78d5656-6b3d-4495-8fdf-e2464a8c7296` processed 310 symbols: 295 succeeded and 15 failed.
- Seven failures reported `Could not read JSON` / Redis deserialization errors for symbols that had already been populated during startup.
- The raw Yahoo client caches `QuoteSummaryResponse` and `ChartResponse` together under `yahoo-finance` with keys prefixed `yf:v2:`.
- Normalized market-data caches use `mdc-*` cache names and a separate schema version in `CacheKeyHelper`.
- `RedisCacheConfig` currently applies one `GenericJackson2JsonRedisSerializer` to heterogeneous cache values.
- Existing cache tests use `spring.cache.type=simple`; they prove cache hits but do not exercise Redis serialization or restart compatibility.
- `CacheEvictionService` evicts normalized `mdc-*` entries but not raw Yahoo entries.

## Scope

### 1. Reproducible cache contract

- Add a real-Redis integration test that writes and reads every production cache value family through the configured Spring `CacheManager`.
- Cover both raw Yahoo DTOs (`QuoteSummaryResponse`, `ChartResponse`) and normalized market-data values (`CompanyProfile`, `FundamentalSnapshot`, `RatioSnapshot`, annual ratio lists, and `MarketPriceQuote`).
- Reproduce the cold-call/warm-call sequence that failed for the seven observed symbols without making live provider calls.
- Record the exact cache name, key, serializer, and value type responsible for the failure before changing behavior.

### 2. Explicit, type-safe serialization contracts

- Replace the single heterogeneous generic serialization contract with explicit cache value contracts.
- Split the raw Yahoo cache into independently typed caches for quote-summary and chart responses, or introduce an equivalently type-safe envelope; do not depend on arbitrary runtime class metadata.
- Configure each normalized cache with the serializer appropriate to its declared value type, including an explicit representation for annual-ratio collections.
- Keep provider DTOs internal. Cached payloads must not be exposed through public APIs, logs, fixtures, or committed evidence.
- Centralize cache schema/namespace versions so a value-shape change cannot update one layer while leaving another layer stale.

### 3. Safe compatibility and self-healing

- Advance affected cache namespaces for the rollout. Old entries must age out by TTL and must never be interpreted using the new contract.
- Add a narrowly scoped cache read-error policy: when a value cannot be deserialized, evict only that cache entry, record sanitized diagnostics, and treat the read as a cache miss so the normal provider/fallback path can repopulate it once.
- Do not swallow provider, database, programming, or write-path failures under the cache recovery policy.
- Prevent infinite retry loops: one failed read becomes one eviction and one ordinary method execution.
- Do not use `FLUSHDB` as a production migration strategy. Redis also contains authentication/session material; market-data cache invalidation must be namespace- or entry-scoped.

### 4. Complete eviction semantics

- Extend symbol eviction to remove both normalized market-data entries and all raw Yahoo entries for that symbol.
- Ensure ADMIN cache eviction and reseeding cannot leave an incompatible lower-layer entry that immediately poisons the refreshed upper-layer value.
- Define whether bulk deployment cleanup is TTL-only or uses a bounded prefix scan; any scan must be operationally safe and must not delete unrelated Redis keys.

### 5. Observability and actionable outcomes

- Emit a metric for cache deserialization recovery tagged only by cache name and exception category, never symbol if symbol cardinality is unbounded.
- Emit a sanitized structured log containing cache name, a hashed or safely normalized key reference, schema version, and recovery action.
- Preserve per-symbol seed isolation: a corrupt cache entry must not fail the whole seed run.
- Seed outcomes should distinguish recovered cache incompatibility from provider failure when that distinction is useful to operators, without exposing serialized payloads.

## Decisions

1. **Treat the seven failures as one systemic defect.** No symbol allowlist, special-case eviction, or retry list will be introduced.
2. **Prefer typed JSON per cache family.** This is safer and more auditable than polymorphic arbitrary-class metadata.
3. **Use namespace migration plus lazy self-healing.** Deployments should not require a global Redis flush.
4. **Retain raw Yahoo caching only if its provider-call reduction is demonstrated.** If it duplicates the normalized `mdc-*` layer without material benefit, removing the raw cache is an acceptable simplification, provided request-volume tests prove quota/rate-limit safety.
5. **Keep recovery narrow.** Only deserialization/type-contract read errors are converted into scoped misses.

## Mission and Architecture Guardrails

- Preserve the mission's cache-first FMP/Yahoo fallback behavior; the fix must reduce provider calls rather than silently disabling caching.
- Preserve source, fallback, freshness, and provenance metadata after cold and warm reads.
- Never fabricate financial data when cache recovery or provider refresh fails.
- Preserve Redis-backed authentication behavior and never log credentials, JWTs, API keys, cookies, raw provider responses, or full cached values.
- Remain within the existing Spring Cache + Redis 7 architecture; no new distributed cache product is introduced.

## Out of Scope

- The seven unrelated `rollback-only` seed failures.
- The `BF.B` symbol-normalization/not-found failure.
- The `job_run_log.scope_symbols varchar(1000)` bootstrap limit.
- Changes to valuation, score, moat, portfolio, or recommendation algorithms.
- A general Redis database redesign or separation of auth and cache databases, which may be assessed separately for production hardening.
