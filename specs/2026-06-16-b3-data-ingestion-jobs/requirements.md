# Requirements — Phase B3: Data Ingestion Jobs

## Context

B1 delivered the `MarketDataClient` abstraction (Yahoo + FMP implementations); B2 added Redis caching in front of every client call. B3 adds the scheduled population layer: the jobs that continuously pull data from FMP and persist it to the local DB so that:

- The Valuation Engine (Group C) can read `FundamentalSnapshot` + `RatioSnapshot` from DB without calling FMP at request time.
- The Screener (Group D) operates entirely on local data — no live FMP calls during screener queries.
- Watchlist and portfolio holders receive fresh quotes, dividends, and insider activity without manual triggers.

This phase requires B1 and B2 to be merged and passing. It does not touch the Valuation Engine or the Score Engine.

## Scope

### In scope

- Flyway migration: `job_run_log` table + `JobRunLog` entity + repository
- `JobRunLogger` service: wraps every job with start/end/error logging in its own transaction
- `IngestionJobHealthIndicator`: Spring Boot health indicator surfaced on `GET /actuator/health`
- `SchedulerConfig`: `@EnableScheduling`, thread pool, `app.jobs.enabled` toggle
- Cron expressions externalized to `application.yml` under `app.jobs.cron`
- All 7 scheduled jobs (see table below)
- `MarketDataClient` interface extensions: `listSymbols`, `getDividendHistory`, `getInsiderTransactions`, `getFmpDcf`
- FMP implementations for all 4 new interface methods
- Yahoo stubs (throw `UnsupportedOperationException`) for the 3 bulk methods; `getFmpDcf` returns `Optional.empty()`
- `POST /api/v1/admin/jobs/{jobName}/run` manual trigger endpoint (ADMIN only)
- Unit tests: `JobRunLoggerTest`, `IngestionJobHealthIndicatorTest`
- Integration tests: `BulkProfileSyncJobIT`, `QuoteRefreshJobIT` (real FMP, real DB)
- `scripts/ingestion-demo.sh`

### Out of scope

- Quartz Scheduler or Spring Batch (see Decision D1)
- Parallel per-symbol processing within a single job (see Decision D2)
- Prometheus metrics for job latency (Phase I2)
- Alert detection based on ingested data (Group G)
- Yahoo Finance implementations of `listSymbols`, `getDividendHistory`, `getInsiderTransactions`
- Frontend changes

## Scheduled Jobs

| Job name | Class | Schedule | Symbol scope | FMP endpoint(s) |
|---|---|---|---|---|
| `bulk-profile-sync` | `BulkProfileSyncJob` | 02:00 nightly | Configured exchanges (NYSE, NASDAQ) | `/stock-list`, `getProfile` |
| `bulk-fundamentals-sync` | `BulkFundamentalsSyncJob` | 03:00 nightly | All `Security` rows in DB | `getFundamentals` |
| `bulk-ratios-sync` | `BulkRatiosSyncJob` | 03:30 nightly | All `Security` rows in DB | `getRatios` |
| `bulk-dcf-sync` | `BulkDcfSyncJob` | 04:00 nightly | All `Security` rows in DB | `/discounted-cash-flow/{symbol}` |
| `quote-refresh` | `QuoteRefreshJob` | Every 15 min | Active watchlist + holding symbols | `getQuote` |
| `dividend-update` | `DividendUpdateJob` | 06:00 nightly | Active watchlist + holding symbols | `/historical-price-full/stock_dividend/{symbol}` |
| `insider-trading` | `InsiderTradingJob` | Hourly | Active watchlist + holding symbols | `/insider-trading?symbol={symbol}` |

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | Spring `@Scheduled` + `ThreadPoolTaskScheduler` (not Quartz, not Spring Batch) | MVP is single-node; `@Scheduled` is on the classpath with no additional dependencies. Migrate to Quartz when multi-node deployment is required. |
| D2 | Per-symbol sequential iteration (not parallelised within a job) | FMP Premium allows 300 req/min. A nightly job over ~1,000 symbols at ≤ 10 req/s is well within the limit. Parallelism adds complexity with no material throughput gain at this scale. |
| D3 | Cron expressions externalized to `application.yml` | Allows per-environment override (e.g. more frequent refresh in staging) without recompile. |
| D4 | `app.jobs.enabled = false` in `application-test.yml` | Prevents `@Scheduled` triggers from firing during unit tests. Integration tests invoke job methods directly. |
| D5 | Historical data appended, never overwritten (immutable records) | Aligns with mission design principle: corrections append new records; old snapshots are preserved for audit and backtesting. Keyed on `(symbol, periodEnd, periodType)` for fundamentals/ratios. |
| D6 | `JobRunLogger` uses `REQUIRES_NEW` propagation | Ensures the log record is committed even when the job body throws and its outer transaction rolls back. |
| D7 | `BulkDcfSyncJob` persists FMP DCF as `ValuationResult` with `source = "FMP_DCF"` | Keeps FMP's precomputed DCF separate from our own (`source = "INTERNAL"`, Group C). Both are queryable from the same table; the Security Detail API (Group E) can display both. |
| D8 | `BulkProfileSyncJob` also upserts `Security` rows from `/stock-list` | Makes the job self-bootstrapping: no separate "seed securities" step needed. New tickers on an exchange appear automatically on the next nightly run. |
| D9 | Manual trigger endpoint `POST /api/v1/admin/jobs/{jobName}/run` | Allows testing without waiting for a cron window; shareable in the ingestion-demo.sh script. |

## Data Rules

| Entity | Key for deduplication | Write strategy |
|---|---|---|
| `Security` | `symbol` | Upsert (insert or update name/sector/country/exchange) |
| `FundamentalSnapshot` | `(symbol, periodEnd, periodType)` | Append only — skip if key exists |
| `RatioSnapshot` | `(symbol, periodEnd)` | Append only — skip if key exists |
| `ValuationResult` (FMP DCF) | `(symbol, source, DATE(calculatedAt))` | Skip if `FMP_DCF` result for today exists |
| `PriceQuote` | `(symbol, quotedAt)` truncated to minute | Upsert |
| `DividendRecord` | `(symbol, exDividendDate)` | Upsert |
| `InsiderTrade` | `(symbol, filingDate, transactionDate, reportingName)` | Upsert |

## Environment Variables (additions)

```
app.jobs.exchanges   Comma-separated exchange codes for BulkProfileSyncJob (default: NYSE,NASDAQ)
```

All other required variables (`FMP_API_KEY`, `REDIS_HOST`, `REDIS_PORT`, `DATABASE_URL`) were declared in previous phases.
