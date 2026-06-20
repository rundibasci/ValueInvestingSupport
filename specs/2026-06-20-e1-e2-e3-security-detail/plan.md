# Plan — Group E: Security Detail API (M5)

## Task Group 1: E1 — Search & Company Profile Endpoint

1.1 Create `SecuritySearchController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/search?q=` — autocomplete by name or symbol; returns up to 10 matches; any authenticated role
  - Delegates to `SecurityRepository.findTop10BySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(q, q)`
  - Returns `List<SecuritySearchItem>`
  - If `q` is blank or absent: return empty list (no DB query)
  - 401 if unauthenticated

1.2 Create `SecurityProfileController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}` — full profile: company info from `Security` + latest `FundamentalSnapshot` (annual) + latest `RatioSnapshot` + latest `PriceQuote`
  - 404 if symbol not in `security` table
  - 422 if the most recent annual `FundamentalSnapshot.snapshotDate` is older than 7 days (stale data guard — mirrors Val1)
  - Returns `SecurityDetailResponse`

1.3 Create DTOs in `it.mazzoni.vis.security.dto`:
  - `SecuritySearchItem` record: `String symbol`, `String companyName`, `String sector`, `String exchange`
  - `SecurityDetailResponse` record: `symbol`, `companyName`, `sector`, `exchange`, `country`, `currency`, `marketCap`, `description`, `ceo`, `employees`, `website`, `ipoDate`, `currentPrice`, `priceDate`, `revenue`, `netIncome`, `fcf`, `eps`, `bvps`, `pe`, `roic`, `dividendYield`, `dataAsOf`
  - Static factory: `SecurityDetailResponse.from(Security s, FundamentalSnapshot f, RatioSnapshot r, PriceQuote p)`

1.4 Add to `SecurityRepository`:
  - `List<Security> findTop10BySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(String sym, String name)`

1.5 Add to `FundamentalSnapshotRepository`:
  - `Optional<FundamentalSnapshot> findTopBySecuritySymbolAndPeriodTypeOrderBySnapshotDateDesc(String symbol, String periodType)`

1.6 Add to `RatioSnapshotRepository`:
  - `Optional<RatioSnapshot> findTopBySecuritySymbolOrderBySnapshotDateDesc(String symbol)`

1.7 Add to `PriceQuoteRepository`:
  - `Optional<PriceQuote> findTopBySecuritySymbolOrderByQuoteDateDesc(String symbol)`

1.8 Unit test: `SecuritySearchControllerTest` (MockMvc, mocked repository):
  - `?q=AAPL` → 200, JSON array containing `symbol="AAPL"`
  - `?q=` (blank) → 200, empty array
  - Unauthenticated → 401

1.9 Unit test: `SecurityProfileControllerTest` (MockMvc, mocked service):
  - Known symbol, fresh snapshot → 200, all fields present
  - Unknown symbol → 404
  - Known symbol, snapshot date > 7 days ago → 422 with message `"Data is stale for {symbol}: last snapshot {date}"`
  - Unauthenticated → 401

---

## Task Group 2: E1 — Financials & Ratios Endpoints

2.1 Create `FinancialsController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/financials`
  - Loads up to 10 annual `FundamentalSnapshot` records (periodType=ANNUAL, ordered `snapshotDate DESC`)
  - Loads up to 8 quarterly `FundamentalSnapshot` records (periodType=QUARTERLY, ordered `snapshotDate DESC`)
  - Loads 1 TTM `FundamentalSnapshot` record (periodType=TTM, most recent)
  - 404 if symbol not in DB; 422 if no annual snapshots found
  - Returns `FinancialsResponse`

2.2 Create `RatiosController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/ratios`
  - Loads up to 10 `RatioSnapshot` records for the symbol ordered by `snapshotDate DESC`
  - 404 if symbol not in DB
  - Returns `RatiosHistoryResponse`

2.3 Create DTOs in `it.mazzoni.vis.security.dto`:
  - `AnnualFinancials` record: `int fiscalYear`, `BigDecimal revenue`, `BigDecimal netIncome`, `BigDecimal fcf`, `BigDecimal eps`, `BigDecimal bvps`
  - `QuarterlyFinancials` record: `String period`, `BigDecimal revenue`, `BigDecimal netIncome`, `BigDecimal fcf`, `BigDecimal eps`
  - `TtmFinancials` record: `BigDecimal revenue`, `BigDecimal netIncome`, `BigDecimal fcf`, `BigDecimal eps`
  - `FinancialsResponse` record: `String symbol`, `List<AnnualFinancials> annuals`, `List<QuarterlyFinancials> quarters`, `TtmFinancials ttm`
  - `RatioSnapshotItem` record: `LocalDate date`, `BigDecimal pe`, `BigDecimal roic`, `BigDecimal roe`, `BigDecimal debtToEquity`, `BigDecimal grossMargin`, `BigDecimal fcfMargin`, `BigDecimal dividendYield`
  - `RatiosHistoryResponse` record: `String symbol`, `List<RatioSnapshotItem> ratios`

2.4 Add to `FundamentalSnapshotRepository`:
  - `List<FundamentalSnapshot> findTop10BySecuritySymbolAndPeriodTypeOrderBySnapshotDateDesc(String symbol, String periodType)`

2.5 Unit test: `FinancialsControllerTest` (MockMvc, mocked service/repository):
  - 200 with `annuals.size() ≤ 10`, `quarters.size() ≤ 8`, `ttm` non-null
  - 404 for unknown symbol
  - 401 unauthenticated

2.6 Unit test: `RatiosControllerTest` (MockMvc, mocked repository):
  - 200 with `ratios.size() ≤ 10`
  - 404 for unknown symbol
  - 401 unauthenticated

---

## Task Group 3: E2 — Dividends, Insiders & Growth

3.1 Create `DividendsController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/dividends`
  - Loads all `DividendRecord` rows for the symbol ordered by `paymentDate DESC`
  - Delegates streak + CAGR computation to `DividendsService`
  - 404 if symbol not in DB
  - Returns `DividendsResponse`

3.2 Create `DividendsService` in `it.mazzoni.vis.security`:
  - `computeStreak(List<DividendRecord>) → int`: count consecutive calendar years (from current year backward) in which at least one dividend was paid
  - `computeCagr(List<DividendRecord>, int years) → BigDecimal`: sum DPS per calendar year; apply formula `((latestAnnualDPS / baseAnnualDPS)^(1.0/years) - 1) * 100`; return null if fewer than `years + 1` calendar years of data are present

3.3 Create `InsidersController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/insiders`
  - Loads `InsiderTrade` rows for the symbol where `transactionDate >= LocalDate.now().minusMonths(12)`, ordered by `transactionDate DESC`
  - 404 if symbol not in DB
  - Returns `InsidersResponse`

3.4 Create `GrowthController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/growth`
  - Loads up to 11 annual `FundamentalSnapshot` records (to allow 10y CAGR window)
  - Delegates to `GrowthService.compute(symbol, snapshots)`
  - 404 if symbol not in DB
  - Returns `GrowthResponse`

3.5 Create `GrowthService` in `it.mazzoni.vis.security`:
  - `compute(String symbol, List<FundamentalSnapshot> annuals) → GrowthResponse`
  - CAGR formula: `((endValue / startValue)^(1.0/years) - 1) * 100`
  - Return null for any window where fewer than `years + 1` annual records are available
  - Compute separately for `revenue`, `fcf`, `eps`

3.6 Create `PeersController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/peers`
  - Loads the subject `Security`; finds up to 5 other `Security` rows in the same `sector` ordered by `ABS(peer.marketCap - subject.marketCap)` ascending (closest market cap first)
  - For each peer: loads latest `RatioSnapshot`, latest `ValuationResult`, latest `ValueScore`
  - Returns `PeersResponse` (empty `peers` list acceptable if no other securities exist in the same sector)
  - 404 if subject symbol not in DB

3.7 Add to `SecurityRepository`:
  - `List<Security> findTop5BySectorAndSymbolNotOrderByMarketCapAsc(String sector, String symbol)` — approximation; post-sort by absolute delta in service if needed

3.8 Add to `InsiderTradeRepository`:
  - `List<InsiderTrade> findBySecuritySymbolAndTransactionDateAfterOrderByTransactionDateDesc(String symbol, LocalDate since)`

3.9 Create DTOs in `it.mazzoni.vis.security.dto`:
  - `DividendItem` record: `LocalDate paymentDate`, `BigDecimal amount`, `String currency`
  - `DividendsResponse` record: `String symbol`, `List<DividendItem> history`, `int streak`, `BigDecimal cagr3y`, `BigDecimal cagr5y`, `BigDecimal cagr10y`
  - `InsiderTradeItem` record: `LocalDate transactionDate`, `String name`, `String title`, `String transactionType`, `Long shares`, `BigDecimal pricePerShare`, `BigDecimal totalValue`
  - `InsidersResponse` record: `String symbol`, `List<InsiderTradeItem> trades`
  - `GrowthMetrics` record: `BigDecimal cagr3y`, `BigDecimal cagr5y`, `BigDecimal cagr10y`
  - `GrowthResponse` record: `String symbol`, `GrowthMetrics revenue`, `GrowthMetrics fcf`, `GrowthMetrics eps`
  - `PeerItem` record: `String symbol`, `String companyName`, `BigDecimal currentPrice`, `BigDecimal compositeFairValue`, `BigDecimal marginOfSafety`, `BigDecimal totalScore`, `BigDecimal pe`, `BigDecimal roic`
  - `PeersResponse` record: `String symbol`, `List<PeerItem> peers`

3.10 Unit tests: `DividendsControllerTest`, `InsidersControllerTest`, `GrowthControllerTest`, `PeersControllerTest`
  - MockMvc, mocked services
  - 200 with correct shapes; 404 for unknown symbol; 401 unauthenticated
  - `DividendsControllerTest`: assert `streak ≥ 0`; assert `cagr3y` is null when service returns null (< 4 years of data)
  - `GrowthControllerTest`: `revenue.cagr3y` non-null when ≥ 4 annual snapshots supplied; null when fewer provided

---

## Task Group 4: E3 — Full Valuation + Analyst Estimates

4.1 Create `AnalystEstimate` JPA entity in `it.mazzoni.vis.security.domain`:
  - Fields: `Long id`, `Security security`, `String analystFirm`, `BigDecimal targetPrice`, `String ratingLabel` (BUY / HOLD / SELL), `LocalDate targetDate`, `LocalDateTime createdAt`
  - `@ManyToOne(fetch = FetchType.LAZY) Security security`
  - `@Table(name = "analyst_estimate")`

4.2 Create `AnalystEstimateRepository` extending `JpaRepository<AnalystEstimate, Long>`:
  - `List<AnalystEstimate> findBySecuritySymbolOrderByTargetDateDesc(String symbol)`

4.3 Create `SecurityValuationController` in `it.mazzoni.vis.security`:
  - `GET /api/v1/securities/{symbol}/valuation`
  - Loads latest `ValuationResult` from DB; 422 if none found
  - Loads all `AnalystEstimate` rows; if non-empty, computes:
    - `priceTargetMean = avg(targetPrice)` (rounded to 2dp)
    - `priceTargetLow = min(targetPrice)`, `priceTargetHigh = max(targetPrice)`
    - `analystCount = list.size()`
    - `consensus`: majority `ratingLabel` (BUY > HOLD > SELL as tiebreaker)
  - If list is empty: `analystEstimates = null`
  - MiFID II disclaimer mandatory in response body
  - 404 if symbol not in `security` table

4.4 Create DTOs in `it.mazzoni.vis.security.dto`:
  - `DcfScenarios` record: `BigDecimal base`, `BigDecimal low`, `BigDecimal high`, `BigDecimal enterpriseValue`
  - `AnalystEstimatesItem` record: `BigDecimal priceTargetMean`, `BigDecimal priceTargetLow`, `BigDecimal priceTargetHigh`, `int analystCount`, `String consensus`
  - `ValuationDetailResponse` record: `String symbol`, `String companyName`, `BigDecimal currentPrice`, `DcfScenarios dcf`, `BigDecimal grahamNumber`, `BigDecimal ddmValue`, `BigDecimal fmpDcfValue`, `BigDecimal compositeFairValue`, `BigDecimal marginOfSafety`, `BigDecimal mosLow`, `BigDecimal mosHigh`, `String recommendation`, `AnalystEstimatesItem analystEstimates`, `LocalDate dataAsOf`, `String disclaimer`
  - `disclaimer` constant: `"This is a decision-support tool, not investment advice (MiFID II)."`
  - `mosLow` = MoS computed from `ValuationResult.fairValueLow` (DCF pessimistic) vs current price; `mosHigh` from `ValuationResult.fairValueHigh` (DCF optimistic)
  - `fmpDcfValue` sourced from `ValuationResult.fmpDcfValue` if the field exists; null otherwise

4.5 Unit test: `SecurityValuationControllerTest` (MockMvc, mocked repositories):
  - 200 with full `ValuationDetailResponse` shape; `dcf.base` non-null; `grahamNumber` present
  - `ddmValue` null acceptable (RULE-07 guard honoured)
  - `analystEstimates` null when repository returns empty list
  - `analystEstimates.analystCount = 3`, `consensus = "BUY"` when 2 BUY + 1 HOLD estimates seeded
  - `disclaimer` field equals the MiFID II string
  - 404 for unknown symbol; 422 when no `ValuationResult` exists; 401 unauthenticated

---

## Task Group 5: Flyway Migrations

5.1 Add migration `V{N}__security_detail_indexes.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_security_name
    ON security(name);

  CREATE INDEX IF NOT EXISTS idx_fundamental_snapshot_security_period_date
    ON fundamental_snapshot(security_id, period_type, snapshot_date DESC);

  CREATE INDEX IF NOT EXISTS idx_ratio_snapshot_security_date
    ON ratio_snapshot(security_id, snapshot_date DESC);

  CREATE INDEX IF NOT EXISTS idx_dividend_record_security_date
    ON dividend_record(security_id, payment_date DESC);

  CREATE INDEX IF NOT EXISTS idx_insider_trade_security_date
    ON insider_trade(security_id, transaction_date DESC);
  ```

5.2 Add migration `V{N+1}__analyst_estimate.sql`:
  ```sql
  CREATE TABLE analyst_estimate (
    id          BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES security(id),
    analyst_firm VARCHAR(100),
    target_price NUMERIC(19,4),
    rating_label VARCHAR(10),
    target_date  DATE,
    created_at   TIMESTAMPTZ DEFAULT NOW()
  );

  CREATE INDEX idx_analyst_estimate_security_date
    ON analyst_estimate(security_id, target_date DESC);
  ```

5.3 Verify both migrations apply cleanly in sequence: `mvn flyway:migrate -pl backend`

---

## Task Group 6: Integration Test (Testcontainers PostgreSQL)

6.1 Create `SecurityDetailIT` in `backend/src/test/java/.../security/`:
  - `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  - `@ActiveProfiles({"test", "security-detail-test"})`
  - `@Testcontainers` + `@Container static PostgreSQLContainer<?> postgres`
  - `@DynamicPropertySource` → sets `spring.datasource.url/username/password`
  - Seed via `@BeforeAll` using `JdbcTemplate` (not service layer):
    - 1 `security` row (`symbol=AAPL, name=Apple Inc., sector=Technology, exchange=NASDAQ`)
    - 10 `fundamental_snapshot` rows (`period_type=ANNUAL`, dates stepping back one year each)
    - 8 `fundamental_snapshot` rows (`period_type=QUARTERLY`)
    - 1 `fundamental_snapshot` row (`period_type=TTM`)
    - 10 `ratio_snapshot` rows
    - 5 `dividend_record` rows spanning 5 consecutive calendar years
    - 3 `insider_trade` rows all within last 12 months
    - 1 `valuation_result` row
    - 1 `value_score` row
    - 3 `analyst_estimate` rows (target prices: 190/200/220; ratings: HOLD/BUY/BUY)
    - 1 additional `security` row (`symbol=MSFT, sector=Technology`) for peers test
  - Test cases — all requests carry `Authorization: Bearer <jwt>` obtained via login:
    - `GET /api/v1/securities/search?q=AAPL` → 200; array contains `{ symbol: "AAPL" }`
    - `GET /api/v1/securities/search?q=apple` (partial name, lower-case) → 200; contains AAPL
    - `GET /api/v1/securities/AAPL` → 200; `companyName = "Apple Inc."`, `sector = "Technology"`
    - `GET /api/v1/securities/UNKNOWN` → 404
    - `GET /api/v1/securities/AAPL/financials` → 200; `annuals.size() = 10`; `quarters.size() = 8`; `ttm` non-null
    - `GET /api/v1/securities/AAPL/ratios` → 200; `ratios.size() = 10`
    - `GET /api/v1/securities/AAPL/dividends` → 200; `streak = 5`; `cagr3y` non-null
    - `GET /api/v1/securities/AAPL/insiders` → 200; `trades.size() = 3`
    - `GET /api/v1/securities/AAPL/growth` → 200; `revenue.cagr3y` non-null
    - `GET /api/v1/securities/AAPL/peers` → 200; `peers` list contains `{ symbol: "MSFT" }`
    - `GET /api/v1/securities/AAPL/valuation` → 200; `compositeFairValue` non-null; `disclaimer` = MiFID II string; `analystEstimates.analystCount = 3`; `analystEstimates.consensus = "BUY"`
    - Unauthenticated request to any endpoint → 401

6.2 Create `application-security-detail-test.yml` in `backend/src/test/resources/`:
  ```yaml
  spring:
    jpa:
      show-sql: false
    flyway:
      enabled: true
  ```

---

## Task Group 7: Review & Merge Readiness

7.1 Run all unit tests: `mvn test -pl backend`
7.2 Run `SecurityDetailIT`: `mvn test -pl backend -Dtest=SecurityDetailIT`
7.3 Manual smoke test curl sequence (see validation.md)
7.4 Verify both Flyway migrations apply cleanly: `mvn flyway:migrate -pl backend`
7.5 Merge `phase/group-e-security-detail` → `main` via `/merge-phase`
