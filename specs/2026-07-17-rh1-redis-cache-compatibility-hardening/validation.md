# RH1 Redis Cache Compatibility Hardening — Validation

## Functional Acceptance

- [x] `AAPL`, `JNJ`, `KO`, `PEP`, `PG`, `UNP`, and `XOM` can be seeded and reseeded against a warm Redis cache without deserialization failures.
- [x] A backend restart between cache write and cache read does not change the result or make the entry unreadable.
- [x] Every production cache family has one documented key and value contract with an explicit schema version and TTL.
- [x] An intentionally incompatible market-data entry is evicted individually and treated as a cache miss; the normal intercepted call is then responsible for repopulation.
- [x] Recovery deletes only the addressed entry; unit and live rollout evidence show no global Redis deletion.
- [x] Manual symbol eviction clears normalized and raw provider caches consistently.
- [x] Cold- and warm-cache seed results preserve successful values and source semantics for the seven-symbol replay.
- [x] Provider and non-serialization failures remain fail-fast; no financial value is fabricated by recovery.

## Automated Checks

### Real Redis contract tests

- [x] Round-trip `QuoteSummaryResponse` through its production cache.
- [x] Round-trip `ChartResponse` through its production cache.
- [x] Round-trip `CompanyProfile`, `FundamentalSnapshot`, `RatioSnapshot`, annual-ratio collection, and `MarketPriceQuote`.
- [x] Read entries after a backend restart against the same Redis container in live replay.
- [x] Verify the recovery handler performs scoped eviction for an incompatible payload.
- [x] Verify a corrupt entry for one symbol does not affect a valid entry for another.
- [x] Cacheable market-data boundaries use synchronized loading so concurrent misses/recovery are coalesced per application instance; the seed executor is additionally bounded and sequential.

### Application regression tests

- [x] Cache keys use locale-stable uppercase normalization and centrally versioned namespaces; dotted/dashed aliases continue through the existing symbol contract.
- [x] Cache eviction tests cover every cache family.
- [x] Existing Yahoo cache/fallback regressions pass with the retained raw-cache architecture.
- [x] Existing seed-run regressions pass.
- [x] Recovery tests assert the low-cardinality metric; implementation logs only cache, short key hash, exception category, and action.

## Manual Operational Replay

1. Start the real demo with clean PostgreSQL and Redis.
2. Seed the seven observed regression symbols and retain Redis.
3. Reseed the same symbols and require seven successful or provider-limited outcomes, with zero Redis contract failures.
4. Restart the backend only and repeat the read/reseed workflow.
5. Insert one deliberately incompatible test entry under a non-production key, call the owning workflow, and confirm only that entry is removed and rebuilt.
6. Inspect Redis key names and TTLs without printing values.
7. Run the 310-symbol due-diligence universe and compare outcome categories with seed run `f78d5656-6b3d-4495-8fdf-e2464a8c7296`.

Completed replay: seed run `8a4863aa-96a7-4d11-ac5f-3b45d9f2bb02` processed 310/310 with 302 successes, eight known out-of-scope failures, and zero Redis deserialization failures. The sequential bounded seed executor prevented a recovery stampede; no recovery event was required under the new namespaces.

## Success Thresholds

- Redis deserialization failures: **0** across the seven-symbol replay and 310-symbol replay.
- Cache contract round-trip coverage: **100%** of production cache value families.
- Unscoped Redis deletion: **0**.
- Provider calls on a warm-cache repeat: no regression beyond the documented architecture baseline.
- Cache recovery logs containing raw values, tokens, keys, cookies, or credentials: **0**.
- Seed counts remain monotonic and reconcile with submitted symbols.

## Suggested Commands

- `cd backend && ./mvnw -Dtest='*Redis*Test,*Cache*Test,*Yahoo*Test,*SeedRun*Test' test`
- `cd backend && ./mvnw test`
- `docker compose -f docker-compose.realDemo.yml up --build -d`
- `git diff --check`

Exact test class names may be refined during implementation. Tests requiring sockets should run in the Java 21 Docker/CI environment when the host JDK prevents Testcontainers or WireMock startup.

## Merge Criteria

- The root cause is captured by a failing-before/passing-after real-Redis regression test.
- Serialization contracts, schema versions, and TTL ownership are documented in code or configuration.
- The seven observed symbols pass cold, warm, and post-restart cache workflows.
- The 310-symbol replay contains no Redis deserialization outcome.
- Scoped self-healing is demonstrated without a global Redis flush.
- Authentication and OAuth Redis behavior remains unchanged.
- Full backend tests and `git diff --check` pass.
- No secret or raw provider payload enters source control, fixtures, logs, or validation evidence.

## Risks

- Removing the raw Yahoo cache may increase provider traffic; retain it if measurements show meaningful reuse.
- Polymorphic serializers can introduce security and compatibility risk; prefer declared types and constrained DTOs.
- Lazy eviction can cause a provider stampede after deployment; namespace rollout and bounded concurrency must be verified together.
- A rolling deployment can mix cache writers/readers; versioned namespaces must isolate incompatible application versions.
- Global Redis cleanup can invalidate authentication state; it is explicitly prohibited as the production rollout mechanism.
