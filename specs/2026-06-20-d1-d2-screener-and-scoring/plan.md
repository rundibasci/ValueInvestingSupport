# Plan — Group D: Value Score Engine & Stock Screener API (M4)

## Task Group 1: D1 — Score Endpoint

1.1 Create `ScoreController` in `it.mazzoni.vis.scoring`:
  - `GET /api/v1/securities/{symbol}/score`
  - Security: any authenticated role (`hasAnyRole("ADMIN","ADVISOR","INVESTOR")`)
  - Looks up most recent `ValueScore` for the symbol via `ValueScoreRepository.findTopBySecuritySymbolOrderByScoreDateDesc(symbol)`
  - If no record found but `Security` exists in DB: calls `ValueScoreService.compute(symbol)` inline and returns the result
  - If `Security` not in DB: returns `404 Not Found` with message `"Symbol not found: {symbol}"`
  - Returns `200 OK` with `ValueScoreResponse` (all 5 sub-scores + `totalScore` + `scoreDate`)

1.2 Create `ValueScoreResponse` record in `it.mazzoni.vis.scoring.dto`:
  - Fields: `symbol`, `companyName`, `totalScore`, `mosScore`, `qualityScore`, `safetyScore`, `growthScore`, `dividendScore`, `scoreDate`
  - Static factory: `ValueScoreResponse.from(ValueScore, Security)`

1.3 Unit test: `ScoreControllerTest`
  - MockMvc, mocked `ValueScoreRepository` and `ValueScoreService`
  - Case 1: score exists in DB → 200, correct JSON fields returned, `ValueScoreService.compute` NOT called
  - Case 2: no score in DB, symbol exists → 200, `ValueScoreService.compute` called once
  - Case 3: symbol not in DB → 404
  - Case 4: unauthenticated request → 401

---

## Task Group 2: D2 — Screener DTOs & Specification

2.1 Create `ScreenerRequest` record in `it.mazzoni.vis.screener.dto`:
  - All fields optional (nullable): `sector`, `exchange`, `minMarginOfSafety`, `maxMarginOfSafety`, `minValueScore`, `minRoic`, `maxDebtToEquity`, `minDividendYield`, `minRevenueGrowth`
  - Pagination fields: `page` (default 0), `pageSize` (default 20, max 100 via `@Max`)
  - Sort fields: `sortField` (default `"totalScore"`), `sortDirection` (default `"DESC"`)

2.2 Create `ScreenerResultItem` and `ScreenerResponse` records in `it.mazzoni.vis.screener.dto`:
  - `ScreenerResultItem`: `symbol`, `companyName`, `sector`, `exchange`, `currentPrice`, `compositeFairValue`, `marginOfSafety`, `totalScore`, `mosScore`, `qualityScore`, `safetyScore`, `growthScore`, `dividendScore`, `recommendation`, `scoreDate`
  - `ScreenerResponse`: `List<ScreenerResultItem> results`, `int page`, `int pageSize`, `long totalElements`, `int totalPages`

2.3 Create `SecuritySpecification` in `it.mazzoni.vis.screener`:
  - Implements `Specification<Security>`
  - Static factory: `SecuritySpecification.from(ScreenerRequest)` — returns compound `Predicate`
  - Joins: `Security` → `ValueScore` (subquery: `MAX(scoreDate)` per `security_id`), `Security` → `RatioSnapshot` (most recent), `Security` → `ValuationResult` (most recent)
  - Predicate per filter: skip if field is null; otherwise add `greaterThanOrEqualTo` / `lessThanOrEqualTo` / `equal` as appropriate
  - Unit test: `SecuritySpecificationTest` — verify each predicate type is added when the corresponding field is non-null, and skipped when null

---

## Task Group 3: D2 — ScreenerService & Controller

3.1 Create `ScreenerService` in `it.mazzoni.vis.screener`:
  - Method: `search(ScreenerRequest) → ScreenerResponse`
  - Builds `Specification<Security>` via `SecuritySpecification.from(request)`
  - Builds `Pageable` from `request.page()`, `request.pageSize()`, `Sort.by(direction, field)` with fallback to `totalScore DESC` on unknown sort fields
  - Calls `SecurityRepository.findAll(spec, pageable)` (extend `JpaSpecificationExecutor<Security>`)
  - Maps each `Security` result to `ScreenerResultItem` by loading the `ValueScore` and `ValuationResult` already fetched in the join (no N+1)
  - Returns `ScreenerResponse` with pagination metadata

3.2 Define three preset constants in `ScreenerPresets`:
  ```java
  ScreenerRequest GRAHAM  = new ScreenerRequest(null, null, bd(15), null, null, bd(10), bd(1.0), null, null, "totalScore", "DESC", 0, 20);
  ScreenerRequest DIVIDEND = new ScreenerRequest(null, null, bd(5), null, null, null, null, bd(2.0), null, "totalScore", "DESC", 0, 20);
  ScreenerRequest QUALITY = new ScreenerRequest(null, null, null, null, bd(60), bd(15), bd(1.5), null, null, "totalScore", "DESC", 0, 20);
  ```

3.3 Create `ScreenerController` in `it.mazzoni.vis.screener`:
  - `POST /api/v1/screener` — body: `ScreenerRequest`, returns `ScreenerResponse`; any authenticated role
  - `GET /api/v1/screener/presets` — returns map `{ "graham": ScreenerRequest, "dividend": ScreenerRequest, "quality": ScreenerRequest }`; any authenticated role
  - `GET /api/v1/screener/sectors` — returns `List<String>` of distinct non-null sectors from `security` table; any authenticated role
  - `GET /api/v1/screener/exchanges` — returns `List<String>` of distinct non-null exchanges; any authenticated role

3.4 Add `findDistinctSectors()` and `findDistinctExchanges()` to `SecurityRepository` as `@Query` methods.

3.5 Unit test: `ScreenerControllerTest`
  - MockMvc, mocked `ScreenerService`
  - `POST /api/v1/screener` → 200, correct JSON shape (results array + pagination)
  - `GET /api/v1/screener/presets` → 200, three keys present
  - `GET /api/v1/screener/sectors` → 200, non-empty list
  - `GET /api/v1/screener/exchanges` → 200, non-empty list
  - Unauthenticated call to any endpoint → 401

---

## Task Group 4: Flyway Migration — Screener Indexes

4.1 Add migration `V{N}__screener_indexes.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_value_score_security_date
    ON value_score(security_id, score_date DESC);

  CREATE INDEX IF NOT EXISTS idx_security_sector
    ON security(sector);

  CREATE INDEX IF NOT EXISTS idx_security_exchange
    ON security(exchange);

  CREATE INDEX IF NOT EXISTS idx_ratio_snapshot_security_date
    ON ratio_snapshot(security_id, snapshot_date DESC);

  CREATE INDEX IF NOT EXISTS idx_valuation_result_security_date
    ON valuation_result(security_id, valuation_date DESC);
  ```

4.2 Verify migration applies cleanly: `mvn flyway:migrate -pl backend -Dflyway.url=...`

---

## Task Group 5: Integration Test (Testcontainers PostgreSQL)

5.1 Create `ScreenerIT` in `backend/src/test/java/.../screener/`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - `@ActiveProfiles({"test", "screener-test"})`
  - `@Testcontainers` — `@Container static PostgreSQLContainer<?> postgres`
  - `@DynamicPropertySource` → sets `spring.datasource.url/username/password` from container
  - Setup (`@BeforeAll`): insert 5 000 `Security` rows + corresponding `ValueScore`, `ValuationResult`, `RatioSnapshot` rows via `JdbcTemplate` bulk inserts (not via service layer — for speed)
  - Test cases:
    - No filters → 200, `totalElements` = 5 000, default sort `totalScore DESC` verified on first item
    - `minValueScore=80` → only high-score securities returned; assert all `totalScore ≥ 80`
    - `sector="Technology"` → all results have `sector = "Technology"`
    - `minMarginOfSafety=15, maxDebtToEquity=1.0` (Graham preset) → `marginOfSafety ≥ 15` and `debtToEquity ≤ 1.0` for every item
    - Pagination: `page=1, pageSize=10` → 10 results, `page=1` in response, no overlap with `page=0` results
    - `GET /api/v1/screener/sectors` → list contains "Technology" and "Consumer Staples"
    - Performance assertion: `POST /api/v1/screener` with no filters completes in < 500 ms (measured via `System.currentTimeMillis()` around the `RestTemplate` call)

5.2 Create `application-screener-test.yml` in `backend/src/test/resources/`:
  - Enables Flyway for the test container
  - Sets `spring.jpa.show-sql=false` (suppress N+1 noise in CI logs)

---

## Task Group 6: Review & Merge Readiness

6.1 Run all unit tests: `mvn test -pl backend`
6.2 Run `ScreenerIT`: `mvn test -pl backend -Dtest=ScreenerIT`
6.3 Manual smoke test: login → `POST /api/v1/screener` with Graham preset body → inspect response
6.4 Verify `GET /api/v1/securities/{symbol}/score` returns a valid `ValueScoreResponse` for a previously seeded symbol
6.5 Merge `phase/group-d-screener` → `main` via `/merge-phase`
