# Plan — Phase Val1: Single-Stock Analysis Endpoint

## Task Group 1 — Default Valuation Parameters Config

**1.1** Add `ValuationDefaultsProperties` record in `it.mazzoni.vis.valuation`:
```java
@ConfigurationProperties("valuation.defaults")
public record ValuationDefaultsProperties(
    BigDecimal wacc,
    BigDecimal growthY1Y5,
    BigDecimal growthY6Y10,
    BigDecimal terminalRate
) {}
```

**1.2** Register with `@EnableConfigurationProperties` (alongside existing `ValuationWeightsProperties`).

**1.3** Add defaults to `application.yml`:
```yaml
valuation:
  defaults:
    wacc: 0.09
    growth-y1-y5: 0.08
    growth-y6-y10: 0.04
    terminal-rate: 0.025
```

`requiredReturn` and `dividendGrowthRate` are intentionally absent — DDM is skipped in
quick-analysis (requires user-supplied inputs to be meaningful).

---

## Task Group 2 — QuickAnalysisService

Create `it.mazzoni.vis.api.QuickAnalysisService` (Spring `@Service`).

**2.1** Method signature:
```java
public QuickAnalysisResponse analyze(String symbol)
```

**2.2** Resolve `Security` by `symbol` (case-insensitive). Throw `SecurityNotFoundException` (404)
if absent.

**2.3** Load latest `FundamentalSnapshot` for the security (TTM preferred, fall back to ANNUAL):
- No snapshot → throw `ValuationDataUnavailableException` (422)
- `snapshot.reportDate` older than 7 days → throw `StaleDataException` (422) with the date

**2.4** Load latest `RatioSnapshot` for the security (for `financialSummary` fields).

**2.5** Fetch current price:
1. Look up Redis cache key `mdc:fmp:quote:{symbol}` (via `RedisTemplate` or the existing cache
   abstraction) — extract `price` from cached value if present.
2. Fall back to `priceQuoteRepository.findTopBySecurityOrderByQuoteDateDesc(security)`.
3. If neither yields a price, set `currentPrice = null`.

**2.6** Build `ValuationParams` from `ValuationDefaultsProperties` (no DDM params):
```java
ValuationParams params = new ValuationParams(
    defaults.wacc(), defaults.growthY1Y5(), defaults.growthY6Y10(),
    defaults.terminalRate(), null, null
);
```

**2.7** Call `valuationService.calculate(symbol, params)` → `ValuationResult`.
Catch `ValuationNotApplicableException` → rethrow as 422.

**2.8** Map to `QuickAnalysisResponse` (see Task Group 3).

---

## Task Group 3 — Response DTO

Create `it.mazzoni.vis.api.QuickAnalysisResponse` record (or class with builder):

```java
public record QuickAnalysisResponse(
    String symbol,
    String companyName,
    BigDecimal currentPrice,
    String currency,
    String sector,
    FinancialSummary financialSummary,
    ValuationSummary valuation,
    BigDecimal marginOfSafety,
    String recommendation,
    String disclaimer,
    LocalDate dataAsOf,
    String source
) {
    public record FinancialSummary(
        Long revenue, Long netIncome, Long fcf, BigDecimal eps
    ) {}

    public record ValuationSummary(
        DcfRange dcf, BigDecimal grahamNumber, BigDecimal composite
    ) {}

    public record DcfRange(
        BigDecimal fairValue, BigDecimal low, BigDecimal high
    ) {}
}
```

- `disclaimer` is always `"This is a decision-support tool, not investment advice (MiFID II)."`
- `source` is always `"fmp"`
- `dataAsOf` comes from `FundamentalSnapshot.reportDate`
- `valuation.dcf` is `null` when DCF was not computed (RULE-06 guard)

---

## Task Group 4 — Controller

Create `it.mazzoni.vis.api.QuickAnalysisController`:

```java
@RestController
@RequestMapping("/api/v1/securities")
public class QuickAnalysisController {

    @GetMapping("/{symbol}/quick-analysis")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<QuickAnalysisResponse> quickAnalysis(
            @PathVariable String symbol) {
        return ResponseEntity.ok(quickAnalysisService.analyze(symbol.toUpperCase()));
    }
}
```

**4.1** Exception → HTTP mapping (via existing `@ControllerAdvice`):
- `SecurityNotFoundException` → 404
- `ValuationDataUnavailableException` → 422
- `StaleDataException` → 422
- `ValuationNotApplicableException` → 422

---

## Task Group 5 — Seed Data

**5.1** Create test SQL fixture `backend/src/test/resources/sql/seed_aapl_snapshot.sql`:
- `INSERT INTO security (symbol, company_name, sector, currency, ...)` for AAPL
- `INSERT INTO fundamental_snapshot (security_id, period, report_date, eps_diluted, total_equity, shares_outstanding, total_debt, cash, free_cash_flow, revenue, net_income, ...)` with `report_date = CURRENT_DATE - 1` (always fresh)
- `INSERT INTO ratio_snapshot (...)` with representative ratios
- `INSERT INTO price_quote (security_id, quote_date, close, ...)` with a recent price

**5.2** Create cleanup fixture `backend/src/test/resources/sql/cleanup.sql`:
```sql
DELETE FROM price_quote WHERE security_id IN (SELECT id FROM security WHERE symbol = 'AAPL');
DELETE FROM ratio_snapshot WHERE security_id IN (SELECT id FROM security WHERE symbol = 'AAPL');
DELETE FROM fundamental_snapshot WHERE security_id IN (SELECT id FROM security WHERE symbol = 'AAPL');
DELETE FROM security WHERE symbol = 'AAPL';
```

**5.3** Create Flyway demo migration `backend/src/main/resources/db/demo/V_demo_1__seed_aapl.sql`
with synthetic but plausible AAPL fundamentals (fixed `report_date`, current enough to pass 7-day
guard relative to demo profile startup). Location registered in `application-demo.yml`:
```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/demo
```

---

## Task Group 6 — Integration Test

Create `it.mazzoni.vis.api.QuickAnalysisIT`:

**6.1** Test class setup:
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles({"test"})
@Sql(scripts = "/sql/seed_aapl_snapshot.sql", executionPhase = BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/cleanup.sql", executionPhase = AFTER_TEST_CLASS)
```
H2 in-memory DB via `application-test.yml`. `MarketDataClient` mocked with `@MockBean`
(no real FMP calls; Redis cache returns empty so price falls back to `PriceQuote` in DB).

**6.2** Test: happy path
- Obtain JWT: `POST /auth/login` with admin credentials
- `GET /api/v1/securities/AAPL/quick-analysis` with `Authorization: Bearer <token>`
- Assert 200 OK
- Assert `symbol = "AAPL"`, `marginOfSafety` non-null, `recommendation` is one of
  `STRONG_BUY`, `QUALITY_VALUE`, `FAIR_VALUE`, `OVERVALUED`
- Assert `disclaimer` equals MiFID II string exactly
- Assert `dataAsOf` non-null, `source = "fmp"`
- Assert `valuation.composite` non-null

**6.3** Test: 404 on unknown symbol
- `GET /api/v1/securities/ZZZZ/quick-analysis` → assert 404

**6.4** Test: 422 on stale snapshot
- Update seeded snapshot's `report_date` to 30 days ago via `@Sql` inline SQL
- `GET /api/v1/securities/AAPL/quick-analysis` → assert 422 with stale-data message

**6.5** Test: 401 when no token provided
- `GET /api/v1/securities/AAPL/quick-analysis` with no `Authorization` header → assert 401
