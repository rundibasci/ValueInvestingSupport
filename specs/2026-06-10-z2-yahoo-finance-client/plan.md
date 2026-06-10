# Plan — Z2: Yahoo Finance Client

## Task Group 1: Dependencies & WebClient Configuration

1.1. Add `spring-boot-starter-webflux` (WebClient) and `caffeine` + `spring-boot-starter-cache`
     dependencies to `pom.xml` (if not already present from Z1 scaffold).

1.2. Create `YahooFinanceWebClientConfig` — `@Configuration` bean that produces a `WebClient`
     with base URL `https://query1.finance.yahoo.com`, connect timeout 5 s, read timeout 10 s,
     and JSON codecs configured.

1.3. Create `CacheConfig` — `@Configuration` + `@EnableCaching` bean that registers a Caffeine
     `CacheManager` with a single named cache `"yahoo-finance"` and 15-min expire-after-write TTL.

---

## Task Group 2: Response DTOs (Java Records)

All DTOs live in package `com.valueinvesting.demo.client.yahoo.dto`.

2.1. Wrapper types:
     - `QuoteSummaryResponse(QuoteSummaryResult quoteSummaryResult, String error)`
     - `QuoteSummaryResult(QuoteSummaryResultInner result, String error)`
     (Yahoo wraps the payload in a `quoteSummary.result[0]` array)

2.2. Module DTOs — one record per module, field names matching Yahoo Finance JSON keys:
     - `FinancialDataDto` (currentPrice, totalRevenue, netIncome, freeCashflow, ebitda, …)
     - `DefaultKeyStatisticsDto` (bookValue, sharesOutstanding, trailingEps, forwardEps, …)
     - `IncomeStatementHistoryDto` (list of `IncomeStatementEntry`: totalRevenue, netIncome, …)
     - `BalanceSheetHistoryDto` (list of `BalanceSheetEntry`: totalDebt, cash, …)
     - `CashflowStatementHistoryDto` (list of `CashflowEntry`: totalCashFromOperations, capitalExpenditures, …)
     - `SummaryDetailDto` (trailingPE, dividendYield, marketCap, beta, …)
     - `AssetProfileDto` (sector, industry, longBusinessSummary, country, …)

2.3. Chart DTOs:
     - `ChartResponse(ChartResult chart)`
     - `ChartResult(List<ChartMeta> result)`
     - `ChartMeta(String symbol, String currency, Double regularMarketPrice, …)`

---

## Task Group 3: Yahoo Finance Client

3.1. Define `YahooFinanceClient` interface (package `…client.yahoo`):
     ```
     QuoteSummaryResponse getQuoteSummary(String symbol);
     ChartResponse getChart(String symbol);
     ```

3.2. Implement `YahooFinanceClientImpl`:
     - `getQuoteSummary`: calls `/v10/finance/quoteSummary/{symbol}?modules=financialData,
       defaultKeyStatistics,incomeStatementHistory,balanceSheetHistory,
       cashflowStatementHistory,summaryDetail,assetProfile`
     - `getChart`: calls `/v8/finance/chart/{symbol}`
     - 4xx response where body contains `"No fundamentals data found"` → throw `SymbolNotFoundException`
     - 5xx or `WebClientRequestException` (timeout/connection) → throw `MarketDataUnavailableException`

3.3. Apply `@Cacheable("yahoo-finance")` on both client methods; cache key = `#symbol.toUpperCase()`.

---

## Task Group 4: Domain Adapter

4.1. Create `FundamentalSnapshot` and `RatioSnapshot` Java records in
     `com.valueinvesting.demo.domain` (immutable value objects, no JPA annotations).

4.2. Create `YahooFinanceAdapter` — maps `QuoteSummaryResponse` + `ChartResponse` →
     `FundamentalSnapshot` + `RatioSnapshot`:
     - Extract up to 4 years of `fcf` from `cashflowStatementHistory` (needed for RULE-06)
     - Extract up to 4 years each of revenue, netIncome, eps from their respective histories
     - All numeric Yahoo Finance values are wrapped in `{"raw": …, "fmt": "…"}` objects — use `.raw`

4.3. Null-safe mapping: if a Yahoo Finance field is absent (e.g. no FCF for a bank), map to
     `null` or empty list rather than throwing. Callers (Z3 calculator) apply RULE-06 guards.

---

## Task Group 5: Tests

5.1. Capture JSON fixtures:
     - `src/test/resources/fixtures/yahoo/aapl_quotesummary.json` — real response from
       `quoteSummary?modules=…` for AAPL (captured once, committed to repo)
     - `src/test/resources/fixtures/yahoo/aapl_chart.json` — real response from `chart/AAPL`

5.2. DTO deserialization tests (`YahooDtoDeserializationTest`):
     - Assert Jackson maps fixture JSON → each DTO record without error
     - Spot-check key fields: `financialData.currentPrice.raw`, `defaultKeyStatistics.bookValue.raw`,
       first entry in `incomeStatementHistory`, first `cashflowEntry`

5.3. Adapter mapping tests (`YahooFinanceAdapterTest`):
     - Load AAPL fixtures → run adapter → assert `FundamentalSnapshot` fields match expected values
     - Assert `RatioSnapshot.peRatio` matches `summaryDetail.trailingPE.raw`
     - Assert `fcf` list has up to 4 entries and RULE-06 check can be applied downstream
     - Test null-field resilience: strip `cashflowStatementHistory` from fixture, assert adapter
       returns empty FCF list rather than NPE

5.4. Integration test (`YahooFinanceLiveIT`, `@Tag("integration")`):
     - Calls real Yahoo Finance API for AAPL
     - Asserts non-null currentPrice and non-empty revenue history
     - Excluded from default Surefire run; opt-in via `-Dgroups=integration`
