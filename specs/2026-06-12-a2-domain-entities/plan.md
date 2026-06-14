# Plan — Phase A2: Domain Entities & DB Schema

## Task Group 1 — Securities & Fundamentals Entities

1.1 Create `Security` JPA entity (`domain/entity/Security.java`)
- UUID PK (`@GeneratedValue` strategy `UUID`)
- Fields: `symbol` (unique, not null), `companyName`, `exchange`, `sector`, `industry`, `country`, `currency`, `marketCap`, `description`, `website`, `createdAt`, `updatedAt`
- `@Table(name = "security")`

1.2 Create `FundamentalSnapshot` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `period` (annual/quarterly), `fiscalYear`, `fiscalQuarter`, `reportDate`, revenue, netIncome, operatingIncome, grossProfit, eps, epsDiluted, freeCashFlow, operatingCashFlow, totalAssets, totalLiabilities, totalEquity, totalDebt, cash, sharesOutstanding
- `@Table(name = "fundamental_snapshot")`

1.3 Create `RatioSnapshot` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `period`, `reportDate`, peRatio, pbRatio, psRatio, evToEbitda, roic, roe, roa, debtToEquity, currentRatio, dividendYield, payoutRatio, grossMargin, operatingMargin, netMargin

1.4 Create `PriceQuote` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `quoteDate` (not null, partition key), `open`, `high`, `low`, `close`, `adjustedClose`, `volume`
- `@Table(name = "price_quote")`

## Task Group 2 — Valuation & Scoring Entities

2.1 Create `ValuationResult` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `valuationDate`, `dcfFairValue`, `dcfFairValueLow`, `dcfFairValueHigh`, `grahamNumber`, `ddmFairValue`, `compositeFairValue`, `currentPrice`, `marginOfSafety`, `recommendation` (enum: `STRONG_BUY`, `QUALITY_VALUE`, `FAIR_VALUE`, `OVERVALUED`)
- `@Table(name = "valuation_result")`

2.2 Create `ValueScore` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `scoreDate`, `mosScore`, `qualityScore`, `safetyScore`, `growthScore`, `dividendScore`, `totalScore`
- `@Table(name = "value_score")`

2.3 Create `DividendRecord` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `exDividendDate`, `paymentDate`, `amount`, `currency`, `frequency`
- `@Table(name = "dividend_record")`

2.4 Create `InsiderTrade` entity
- UUID PK; `@ManyToOne` to `Security`
- Fields: `tradeDate`, `insiderName`, `title`, `transactionType` (BUY/SELL), `shares`, `pricePerShare`, `value`
- `@Table(name = "insider_trade")`

## Task Group 3 — User & Portfolio Entities

3.1 Create `User` entity
- UUID PK
- Fields: `email` (unique, not null), `passwordHash` (not null), `role` (enum: `ADVISOR`, `INVESTOR`, `ADMIN`), `active`, `createdAt`
- `@Table(name = "app_user")` (avoid reserved word `user` in PostgreSQL)

3.2 Create `Portfolio` entity
- UUID PK; `@ManyToOne` to `User`
- Fields: `name`, `description`, `createdAt`, `updatedAt`
- `@OneToMany` to `Holding`

3.3 Create `Holding` entity
- UUID PK; `@ManyToOne` to `Portfolio`; `symbol` (not null)
- Fields: `symbol`, `quantity`, `averageCostBasis`, `currency`, `addedAt`

3.4 Create `Watchlist` entity + `WatchlistItem` embeddable/entity
- `Watchlist`: UUID PK; `@ManyToOne` to `User`; `name`
- `WatchlistItem`: UUID PK; `@ManyToOne` to `Watchlist`; `symbol`, `mosAlertMin`, `mosAlertMax`, `addedAt`

3.5 Create `Alert` entity
- UUID PK; `@ManyToOne` to `User`
- Fields: `alertType` (enum), `symbol`, `threshold`, `status` (ACTIVE/ACKNOWLEDGED/DISMISSED), `triggeredAt`, `acknowledgedAt`

## Task Group 4 — Spring Data Repositories

4.1 Create repository interfaces in `domain/repository/`:
- `SecurityRepository` — add `findBySymbol(String) : Optional<Security>`
- `FundamentalSnapshotRepository` — `findBySecurityAndPeriod(...)`
- `RatioSnapshotRepository`
- `PriceQuoteRepository` — `findBySecurityAndQuoteDateBetween(...)`
- `ValuationResultRepository` — `findTopBySecurityOrderByValuationDateDesc(...)`
- `ValueScoreRepository`
- `DividendRecordRepository`
- `InsiderTradeRepository`
- `UserRepository` — `findByEmail(String)`
- `PortfolioRepository` — `findByUser(...)`
- `HoldingRepository`
- `WatchlistRepository`
- `WatchlistItemRepository`
- `AlertRepository`

## Task Group 5 — Flyway Migration V2

5.1 Write `V2__core_schema.sql` in `src/main/resources/db/migration/`:
- Create all tables with `NOT NULL` constraints on required fields
- Add indexes: `security(symbol)`, `fundamental_snapshot(security_id, period, fiscal_year)`, `price_quote(security_id, quote_date)`, `user(email)`, `portfolio(user_id)`, `alert(user_id, status)`
- Create `price_quote` as a range-partitioned table by `quote_date` (monthly)
- Add default monthly partitions for current year + next year
- Verify Flyway checksum is stable (do not edit after first run)

## Task Group 6 — @DataJpaTest Slice Tests

6.1 Write `SecurityRepositoryTest` — save a `Security`, find by symbol, assert fields round-trip
6.2 Write `FundamentalSnapshotRepositoryTest` — save snapshot linked to security, query by period
6.3 Write `UserRepositoryTest` — save user, find by email, verify `passwordHash` not null constraint fires on null
6.4 Write `PortfolioRepositoryTest` — save portfolio linked to user, cascade behavior
6.5 Configure test datasource: use H2 in-memory with PostgreSQL mode (or Testcontainers) — match what A1 chose for the `test` profile
