# Plan — Phase B3: Data Ingestion Jobs

## Group 1 — DB: Job Run Log Table

1. Write Flyway migration `V3__job_run_log.sql`:
   - Table `job_run_log(id BIGSERIAL PK, job_name VARCHAR NOT NULL, started_at TIMESTAMPTZ NOT NULL, completed_at TIMESTAMPTZ, status VARCHAR(20) NOT NULL CHECK(status IN ('RUNNING','SUCCESS','FAILED')), records_processed INT, error_message TEXT)`.
   - Index on `(job_name, started_at DESC)` for efficient last-run queries.
2. Create `JobRunLog` JPA entity (immutable after insert) + `JobRunLogRepository` with `findTop1ByJobNameOrderByStartedAtDesc(String jobName)`.

## Group 2 — Scheduler Infrastructure

3. Add `@EnableScheduling` to a dedicated `SchedulerConfig` `@Configuration` class. Guard with `@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)` so tests can disable all triggers via `app.jobs.enabled=false` in `application-test.yml`.
4. Create `app.jobs` config block in `application.yml`:
   ```yaml
   app:
     jobs:
       enabled: true
       exchanges: NYSE,NASDAQ
       cron:
         bulk-profile:      "0 0 2 * * *"
         bulk-fundamentals: "0 0 3 * * *"
         bulk-ratios:       "0 30 3 * * *"
         bulk-dcf:          "0 0 4 * * *"
         quote-refresh:     "0 */15 * * * *"
         dividend-update:   "0 0 6 * * *"
         insider-trading:   "0 0 * * * *"
   ```
   Bind via `@ConfigurationProperties(prefix = "app.jobs")` record `JobsProperties`.
5. Declare an `AsyncJobExecutor` `@Bean`: `ThreadPoolTaskScheduler` with pool size 4 and thread name prefix `ingestion-`, so nightly jobs can run concurrently without blocking the quote-refresh job.
6. Create `JobRunLogger` `@Service`:
   - `int run(String jobName, Supplier<Integer> task)` — inserts a `RUNNING` record on entry; on success updates to `SUCCESS` with `records_processed` returned by the supplier; on exception updates to `FAILED` with `error_message` then re-throws.
   - All DB writes use `@Transactional(propagation = REQUIRES_NEW)` so the log record is committed even when the job body rolls back.

7. Create `IngestionJobHealthIndicator` `@Component` implementing Spring Boot `HealthIndicator`:
   - For each of the 7 job names, query the last `job_run_log` row.
   - Mark `UP` if every job has a `SUCCESS` row within its expected window: quote-refresh ≤ 20 min, insider-trading ≤ 90 min, all nightly jobs ≤ 26 h.
   - Mark `DOWN` if any job is overdue or has `FAILED` as its latest status; include `{ jobName, status, lastRun }` in the health detail map.

## Group 3 — MarketDataClient Interface Extensions

8. Add the following methods to the `MarketDataClient` interface:
   - `List<SecurityProfile> listSymbols(String exchange)` — FMP: `GET /stock-list` filtered by `exchangeShortName`; Yahoo: throws `UnsupportedOperationException`.
   - `List<DividendRecordDto> getDividendHistory(String symbol)` — FMP: `GET /historical-price-full/stock_dividend/{symbol}`; Yahoo: stub.
   - `List<InsiderTransactionDto> getInsiderTransactions(String symbol)` — FMP: `GET /insider-trading?symbol={symbol}&limit=50`; Yahoo: stub.
   - `Optional<BigDecimal> getFmpDcf(String symbol)` — FMP: `GET /discounted-cash-flow/{symbol}` → `dcf` field; Yahoo: returns `Optional.empty()`.
9. Add corresponding response DTOs (Java records) and FMP adapter mappings. Yahoo stubs compile and throw `UnsupportedOperationException` at runtime (never called in production with `MARKET_DATA_SOURCE=fmp`).

## Group 4 — Bulk Nightly Jobs

10. `BulkProfileSyncJob` (`@Scheduled(cron = "${app.jobs.cron.bulk-profile}")`):
    - For each exchange in `app.jobs.exchanges`: call `listSymbols(exchange)` → upsert `Security` rows (insert if unknown; update `name`, `sector`, `country`, `exchange` if changed).
    - Then call `getProfile(symbol)` for each known `Security` → persist profile fields to `FundamentalSnapshot` (append-only; skip if a snapshot for today already exists).
    - Wrap execution in `jobRunLogger.run("bulk-profile-sync", () -> count)`.

11. `BulkFundamentalsSyncJob` (`@Scheduled(cron = "${app.jobs.cron.bulk-fundamentals}")`):
    - Iterate all `Security` records in DB.
    - Call `getFundamentals(symbol)` → map to `FundamentalSnapshot` records (income, balance, cash flow annual + quarterly); append only — skip any `(symbol, periodEnd, periodType)` combination already present.
    - Wrap in `jobRunLogger.run("bulk-fundamentals-sync", ...)`.

12. `BulkRatiosSyncJob` (`@Scheduled(cron = "${app.jobs.cron.bulk-ratios}")`):
    - Iterate all `Security` records.
    - Call `getRatios(symbol)` → append new `RatioSnapshot` records; skip existing `(symbol, periodEnd)` pairs.
    - Wrap in `jobRunLogger.run("bulk-ratios-sync", ...)`.

13. `BulkDcfSyncJob` (`@Scheduled(cron = "${app.jobs.cron.bulk-dcf}")`):
    - Iterate all `Security` records.
    - Call `getFmpDcf(symbol)` → if present, persist a `ValuationResult` with `source = "FMP_DCF"` and `calculatedAt = NOW()`. Skip if `FMP_DCF` result for today already exists.
    - Wrap in `jobRunLogger.run("bulk-dcf-sync", ...)`.

## Group 5 — Watchlist / Holdings Jobs

14. `QuoteRefreshJob` (`@Scheduled(cron = "${app.jobs.cron.quote-refresh}")`):
    - Collect distinct symbols from all active `WatchlistItem` + `Holding` records.
    - Call `getQuote(symbol)` for each → persist `PriceQuote`; B2 `@Cacheable` on `getQuote` ensures Redis is updated as a side effect.
    - Wrap in `jobRunLogger.run("quote-refresh", ...)`.

15. `DividendUpdateJob` (`@Scheduled(cron = "${app.jobs.cron.dividend-update}")`):
    - Collect symbols from active watchlists + holdings.
    - Call `getDividendHistory(symbol)` → upsert `DividendRecord` rows keyed on `(symbol, exDividendDate)`.
    - Wrap in `jobRunLogger.run("dividend-update", ...)`.

16. `InsiderTradingJob` (`@Scheduled(cron = "${app.jobs.cron.insider-trading}")`):
    - Collect symbols from active watchlists + holdings.
    - Call `getInsiderTransactions(symbol)` → upsert `InsiderTrade` rows keyed on `(symbol, filingDate, transactionDate, reportingName)`.
    - Wrap in `jobRunLogger.run("insider-trading", ...)`.

## Group 6 — Manual Trigger Endpoint (Optional but Recommended)

17. Add `POST /api/v1/admin/jobs/{jobName}/run` (ADMIN only) — looks up the job bean by name from `ApplicationContext` and invokes its `run()` method in a new thread. Returns `202 Accepted` immediately. Makes manual and CI testing straightforward without waiting for a cron window.

## Group 7 — Tests & Demo Script

18. Unit test `JobRunLoggerTest` (H2 in-memory):
    - Happy path: supplier returns `42` → row persisted with `status = SUCCESS`, `records_processed = 42`.
    - Exception path: supplier throws `RuntimeException` → row persisted with `status = FAILED`, `error_message` non-null; exception re-thrown.

19. Unit test `IngestionJobHealthIndicatorTest` (H2):
    - All 7 jobs have `SUCCESS` rows within window → `Health.up()`.
    - One job has `FAILED` as latest row → `Health.down()` with job name in details.
    - One job has no rows at all → `Health.down()`.

20. Integration test `BulkProfileSyncJobIT` (`@SpringBootTest`, `MARKET_DATA_SOURCE=fmp`, real `FMP_API_KEY`):
    - Call `bulkProfileSyncJob.run()` directly.
    - Assert ≥ 1 `Security` row in DB with non-null `sector`.
    - Assert `job_run_log` row with `job_name = 'bulk-profile-sync'` and `status = SUCCESS`.

21. Integration test `QuoteRefreshJobIT`:
    - Seed a `WatchlistItem` for `AAPL`.
    - Call `quoteRefreshJob.run()`.
    - Assert `PriceQuote` row for `AAPL` with `price > 0`.
    - Assert `job_run_log` row with `job_name = 'quote-refresh'` and `status = SUCCESS`.

22. `scripts/ingestion-demo.sh`: documented curl sequence — login → trigger `bulk-profile-sync` via admin endpoint → poll `GET /actuator/health` until `ingestionJobs` is `UP` → print a sample `Security` row.
