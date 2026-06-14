# Plan — B1: Market Data Client Abstraction

## Group 1 — Interface & Configuration

1.1 Add `MarketDataProperties` config class:
   - `@ConfigurationProperties(prefix = "market-data")`
   - Field: `String source` (validated: must be `"yahoo"` or `"fmp"`)
   - Register with `@EnableConfigurationProperties` in a `@Configuration` class

1.2 Define `MarketDataClient` interface in `com.valueinvesting.marketdata`:
   ```java
   public interface MarketDataClient {
       CompanyProfile getProfile(String symbol);
       FundamentalSnapshot getFundamentals(String symbol);
       RatioSnapshot getRatios(String symbol);
       PriceQuote getQuote(String symbol);
   }
   ```

1.3 Create `MarketDataException extends RuntimeException`:
   - Constructor: `MarketDataException(ErrorCode code, String symbol, String message)`
   - `ErrorCode` enum: `NOT_FOUND`, `SERVICE_UNAVAILABLE`, `INVALID_SYMBOL`
   - Add `@ControllerAdvice` handler mapping `NOT_FOUND → 404`, `SERVICE_UNAVAILABLE → 503`

---

## Group 2 — Yahoo Implementation

2.1 Create `YahooMarketDataClient implements MarketDataClient`:
   - `@ConditionalOnProperty(name = "market-data.source", havingValue = "yahoo")`
   - `@Service`
   - Constructor-inject existing `YahooFinanceClient` and `FundamentalAdapter` from Z2

2.2 Implement each interface method by delegating to Z2 components:
   - `getProfile(symbol)` — call `YahooFinanceClient.getAssetProfile()` + `summaryDetail`; map to `CompanyProfile`
   - `getFundamentals(symbol)` — call `YahooFinanceClient.getQuoteSummary()` with income/balance/cashflow modules; map via `FundamentalAdapter` → `FundamentalSnapshot`
   - `getRatios(symbol)` — derive ratios from `financialData` + `defaultKeyStatistics` modules; map to `RatioSnapshot`
   - `getQuote(symbol)` — call `YahooFinanceClient.getChart()` for current price; map to `PriceQuote`

2.3 Map Yahoo 404 / empty response to `MarketDataException(NOT_FOUND, symbol, ...)` and connection errors to `MarketDataException(SERVICE_UNAVAILABLE, symbol, ...)`

---

## Group 3 — FMP Implementation

3.1 Add `spring-boot-starter-webflux` to `pom.xml` if not already present (needed for `WebClient`)

3.2 Create FMP response DTOs (Java records) in `com.valueinvesting.marketdata.fmp.dto`:
   - `FmpProfileDto` — maps `/profile/{symbol}` response
   - `FmpIncomeStatementDto` — maps `/income-statement/{symbol}?limit=5`
   - `FmpBalanceSheetDto` — maps `/balance-sheet-statement/{symbol}?limit=5`
   - `FmpCashFlowDto` — maps `/cash-flow-statement/{symbol}?limit=5`
   - `FmpRatiosDto` — maps `/ratios/{symbol}?limit=5`
   - `FmpQuoteDto` — maps `/quote/{symbol}`

3.3 Create `FmpWebClientConfig`:
   - `@ConditionalOnProperty(name = "market-data.source", havingValue = "fmp")`
   - `@Bean WebClient fmpWebClient(...)` — base URL `https://financialmodelingprep.com/stable/`, default header `apikey: ${FMP_API_KEY}`
   - Configure retry filter: `RetrySpec.backoff(3, Duration.ofSeconds(1))` with `filter(e → is429or503(e))` applied via `.retryWhen()`

3.4 Create `FmpAdapter` — maps FMP DTOs → domain records:
   - `toFundamentalSnapshot(FmpIncomeStatementDto, FmpBalanceSheetDto, FmpCashFlowDto) → FundamentalSnapshot`
   - `toRatioSnapshot(FmpRatiosDto) → RatioSnapshot`
   - `toCompanyProfile(FmpProfileDto) → CompanyProfile`
   - `toPriceQuote(FmpQuoteDto) → PriceQuote`

3.5 Create `FmpMarketDataClient implements MarketDataClient`:
   - `@ConditionalOnProperty(name = "market-data.source", havingValue = "fmp")`
   - `@Service`
   - Constructor-inject `WebClient fmpWebClient` and `FmpAdapter`
   - Each method: call WebClient → `.block()` (sync for now; reactive wrapper added in B2 if needed) → map via adapter
   - 404 from FMP → `MarketDataException(NOT_FOUND, ...)`
   - `WebClientResponseException` on 5xx / retries exhausted → `MarketDataException(SERVICE_UNAVAILABLE, ...)`

---

## Group 4 — WireMock Tests

4.1 Add WireMock to test scope in `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.wiremock</groupId>
       <artifactId>wiremock-standalone</artifactId>
       <version>3.x</version>
       <scope>test</scope>
   </dependency>
   ```

4.2 Create fixture JSON files under `src/test/resources/wiremock/`:
   - `yahoo/quote-summary-AAPL.json` — realistic `quoteSummary` response for AAPL
   - `yahoo/chart-AAPL.json` — realistic `chart` response for AAPL
   - `fmp/profile-AAPL.json`, `fmp/income-AAPL.json`, `fmp/balance-AAPL.json`, `fmp/cashflow-AAPL.json`, `fmp/ratios-AAPL.json`, `fmp/quote-AAPL.json`

4.3 `YahooMarketDataClientTest` (`@SpringBootTest(properties = "market-data.source=yahoo")`):
   - Start WireMock server; point `YahooFinanceClient` base URL at WireMock port
   - Stub `quoteSummary` + `chart` endpoints
   - Assert `getQuote("AAPL").price()` is non-null
   - Assert `getFundamentals("AAPL").revenue()` is non-null and positive
   - Assert `getProfile("AAPL").companyName()` equals fixture value
   - Assert `getProfile("UNKNOWN")` → WireMock returns 404 → `MarketDataException(NOT_FOUND)`
   - Assert WireMock 503 → `MarketDataException(SERVICE_UNAVAILABLE)`

4.4 `FmpMarketDataClientTest` (`@SpringBootTest(properties = "market-data.source=fmp")`):
   - Start WireMock server; set `FMP_API_KEY=test-key`, point FMP base URL at WireMock port
   - Stub all six FMP endpoints with fixture responses
   - Assert `getQuote("AAPL")`, `getProfile("AAPL")`, `getFundamentals("AAPL")`, `getRatios("AAPL")` return correctly mapped domain objects
   - **Retry test**: stub `/profile/RETRY` to return 429 twice then 200; assert result is returned and WireMock recorded exactly 3 calls to that URL
   - Assert 503 on all endpoints → `MarketDataException(SERVICE_UNAVAILABLE)` after 3 attempts (WireMock always 503)
   - Assert 404 → `MarketDataException(NOT_FOUND)` immediately (no retry)
   - Assert `apikey` header is present on every WireMock-captured request
