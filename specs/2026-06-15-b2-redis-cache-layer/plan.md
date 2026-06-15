# Plan — Phase B2: Redis Cache Layer

## Group 1 — Redis Infrastructure & TTL Configuration

1. Add `spring-boot-starter-data-redis` and `spring-boot-starter-cache` to `pom.xml` (if not already present from A1 scaffold).
2. Create `RedisConfig.java` (`@Configuration`, `@EnableCaching`):
   - Define a `RedisCacheManager` bean with a `RedisCacheConfiguration` default (Jackson serialization, no `null` caching).
   - Build named cache configs from TTL properties: `mdc-quote` (15 min), `mdc-ratios` (6 h), `mdc-fundamentals` (6 h), `mdc-profile` (24 h).
3. Add TTL properties to `application.yml` under `app.cache.ttl`:
   ```yaml
   app:
     cache:
       ttl:
         quote: 15m
         ratios: 6h
         fundamentals: 6h
         profile: 24h
   ```
4. Bind properties via a `@ConfigurationProperties(prefix = "app.cache.ttl")` record `CacheTtlProperties`.
5. Confirm Docker Compose `redis` service is present (from A1); no changes needed unless missing.

## Group 2 — `@Cacheable` Wrappers & Key Strategy

6. Annotate each `MarketDataClient` implementation method with `@Cacheable`:
   - `getQuote(symbol)` → cache name `mdc-quote`, key `"'mdc:' + @env.getProperty('MARKET_DATA_SOURCE') + ':quote:' + #symbol.toUpperCase()"`
   - `getProfile(symbol)` → cache name `mdc-profile`, same key pattern with `profile`
   - `getFundamentals(symbol)` → cache name `mdc-fundamentals`, key pattern with `fundamentals`
   - `getRatios(symbol)` → cache name `mdc-ratios`, key pattern with `ratios`

   > Note: annotate on the concrete implementation classes, not the interface, so Spring proxy picks them up correctly.

7. Introduce a `CacheKeyHelper` utility (package-private) to centralise the key-generation expression so it is not duplicated across four annotation strings.

8. Verify that `@Cacheable` on a `@ConditionalOnProperty`-selected bean works correctly (Spring proxy must be applied after bean selection). Add a note in `RedisConfig` if any ordering constraint is required.

## Group 3 — Manual Eviction Endpoint & Tests

9. Create `CacheAdminController`:
   - `DELETE /api/v1/admin/cache/{symbol}` (ADMIN role required)
   - Calls a `CacheEvictionService` that issues `@CacheEvict` on all four cache names for the given symbol.
   - Returns `204 No Content` on success.

10. Unit test `CacheEvictionServiceTest`:
    - Use `SimpleCacheManager` (in-memory, no Redis needed) with `ConcurrentMapCache` instances.
    - Seed cache entries for `AAPL`, call eviction, assert entries are gone.

11. Unit test `MarketDataClientCacheTest` (both Yahoo and FMP profiles):
    - Spy on the underlying client; call `getQuote("AAPL")` twice.
    - Assert the spy was invoked exactly once (second call hit cache).

12. Integration test `RedisCacheIT` (requires live Redis via Testcontainers or Docker Compose):
    - Call `getQuote("AAPL")` → assert Redis key `mdc:fmp:quote:AAPL` exists with TTL ≈ 900 s (15 min).
    - Call `DELETE /api/v1/admin/cache/AAPL` (as ADMIN) → assert Redis key is gone.

13. Update `application-test.yml` to use an embedded/in-memory cache manager (disable Redis) so non-IT unit tests run without a Redis instance.
