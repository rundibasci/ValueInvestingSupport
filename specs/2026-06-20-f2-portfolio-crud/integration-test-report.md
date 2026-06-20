# Integration Test Report — 2026-06-20

Branch: `phase/group-f2-portfolio-crud`
Run date: 2026-06-20
Command: `mvn test -Pintegration-test`

Total: **24 tests — 1 failure, 7 errors, 16 passed**

---

## Passed

| Suite | Tests |
|---|---|
| `YahooFinanceLiveIT` | 2/2 |
| `FmpMarketDataClientLiveIT` | 8/8 |
| `FmpWithYahooFallbackLiveIT` | 6/6 |

---

## Failed

| Suite | Tests | Error type | Root cause |
|---|---|---|---|
| `ValuationDemoIT` | 0F 1E | `DataIntegrityViolation` in `tearDown` | FK constraint: `RATIO_SNAPSHOT` still references `SECURITY(ID)` — cleanup order issue |
| `PipelineDemoIT` | 1F 0E | Assertion failure at line 156 | `pipelineRun_aaplFullPipeline_seedValueateScoreRank` — pipeline assertion failed |
| `PortfolioIT` | 0F 1E | `IllegalState` — ApplicationContext | Schema-validation: missing table `analyst_estimate` (Hibernate can't start) |
| `ScreenerIT` | 0F 1E | `IllegalState` — ApplicationContext | Docker unavailable — Testcontainers cannot find Docker environment |
| `SecurityDetailIT` | 0F 1E | `IllegalState` — ApplicationContext | Docker unavailable — Testcontainers cannot find Docker environment |
| `WatchlistIT` | 0F 1E | `IllegalState` — ApplicationContext | Docker unavailable — Testcontainers cannot find Docker environment |
| `LocalStackDemoIT` | 0F 2E | `IllegalState` — ApplicationContext | Docker unavailable (expected — LocalStack requires Docker) |

---

## Failure Classes

### 1. Docker not available
`LocalStackDemoIT`, `ScreenerIT`, `SecurityDetailIT`, `WatchlistIT` — Testcontainers cannot locate a Docker environment on this machine. `LocalStackDemoIT` failure is expected in this environment.

### 2. Missing DB table `analyst_estimate`
`PortfolioIT` fails at context load because Hibernate schema-validation finds the table missing. The Flyway migration for the new phase may not yet be applied to the Testcontainer DB, or the migration file is not on the classpath used by the test profile.

### 3. FK cleanup order (ValuationDemoIT)
`tearDown` deletes `SECURITY` before `RATIO_SNAPSHOT`, violating the FK constraint. The child table must be cleared first.

### 4. Pipeline assertion (PipelineDemoIT)
`pipelineRun_aaplFullPipeline_seedValueateScoreRank` at line 156 fails an assertion — likely a data or scoring change introduced by the current branch.
