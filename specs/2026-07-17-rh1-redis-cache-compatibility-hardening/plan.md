# RH1 Redis Cache Compatibility Hardening — Plan

## 1. Capture the Failure as a Redis Contract Test

1. Add a Testcontainers Redis integration test using the production `RedisCacheConfig`, not the simple in-memory cache manager.
2. Build deterministic Yahoo quote-summary and chart DTO fixtures without network calls or raw provider payload archives.
3. Exercise write/read and application restart-style read paths for `yahoo-finance` and every `mdc-*` cache.
4. Add a regression sequence matching the live failure: populate the seven symbols once, read them again during reseed, and assert no deserialization exception.
5. Document the exact failing cache/value type in the validation report before selecting the final serializer design.

## 2. Define Explicit Cache Families and Serializers

1. Inventory every `@Cacheable`, `@CachePut`, and `@CacheEvict` declaration and map cache name → key format → value type → TTL → owner.
2. Split `yahoo-finance` into typed quote-summary and chart caches unless the raw layer is removed under the decision gate in Task Group 3.
3. Configure type-safe JSON serialization for each cache family; define an explicit serializable container for `List<RatioSnapshot>` rather than relying on JDK collection implementation types.
4. Keep cache key normalization deterministic for dotted/dashed symbols and uppercase input.
5. Centralize schema versions and add a code-review rule/test requiring a namespace bump whenever a cached DTO or domain value changes incompatibly.

## 3. Resolve the Double-Cache Design Deliberately

1. Measure whether raw Yahoo caching materially reduces calls when `getProfile`, `getFundamentals`, `getRatios`, and `getQuote` are requested for the same symbol.
2. If material, retain the raw layer with separate typed caches and TTLs.
3. If redundant, remove raw `@Cacheable` annotations and rely on normalized `mdc-*` caches, then prove that a normal review/seed workflow stays within expected Yahoo request counts.
4. Record the chosen design and trade-off in `validation-report.md`; do not leave two overlapping layers accidentally.

## 4. Add Scoped Self-Healing for Incompatible Entries

1. Implement a Spring cache error handler or equivalent boundary that recognizes Redis serialization/deserialization failures on reads.
2. Evict only the failing cache/key pair and continue as a miss exactly once.
3. Re-throw unrelated cache connection, provider, database, and programming errors.
4. Add metrics and sanitized structured logs for recovery count, cache family, schema version, and action.
5. Verify concurrent reads of one corrupt entry do not create an unbounded provider-request stampede; use existing synchronization or add bounded single-flight behavior if needed.

## 5. Make Symbol Eviction Complete

1. Extend `CacheEvictionService` to clear raw Yahoo quote-summary/chart entries as well as all normalized market-data entries.
2. Add unit tests asserting exact eviction for each cache family and no effect on other symbols.
3. Add a Redis integration test showing ADMIN eviction followed by reseed returns fresh data without encountering a stale lower-layer value.
4. Confirm dotted/dashed ticker aliases use the same canonical eviction rules as cache writes.

## 6. Roll Out Without Global Redis Deletion

1. Advance the affected Yahoo and normalized market-data namespace versions in one release.
2. Verify old namespaces coexist harmlessly until TTL expiry and new application instances never read them.
3. If immediate cleanup is required, implement/document a bounded market-data-prefix cleanup operation; exclude auth, OAuth, refresh-token, and unrelated application keys.
4. Add deployment and rollback notes covering mixed-version instances and the required ordering for a rolling deployment.

## 7. Regression and Operational Replay

1. Run focused serializer, cache, Yahoo fallback, seed, eviction, and observability tests with real Redis.
2. Run the complete backend test suite and `git diff --check`.
3. Start PostgreSQL and Redis clean, seed the seven regression symbols, then reseed them without clearing Redis.
4. Restart only the backend between two reads and repeat the seven-symbol seed to prove persisted-cache compatibility.
5. Run the full 310-symbol universe once; require zero Redis deserialization failures and reconcile all outcomes by category.
6. Confirm warm-cache results retain the same source, fallback reason, freshness, and financial values as their cold-cache equivalents.
7. Record validation evidence without credentials, API keys, JWTs, cookies, or raw provider payloads.
