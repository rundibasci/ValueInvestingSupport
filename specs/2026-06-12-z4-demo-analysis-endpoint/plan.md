# Plan — Phase Z4: Demo Analysis Endpoint

## Task Group 1 — Response DTO

1. Create `DemoAnalysisResponse` Java record with nested records:
   - `FinancialSummary(revenue, netIncome, fcf, eps)`
   - `DcfValuation(fairValue, low, high)` (nullable)
   - `Valuation(dcf, grahamNumber, composite)`
2. Add `Recommendation` enum: `QUALITY_VALUE`, `UNDERVALUED`, `FAIRLY_VALUED`, `OVERVALUED`
3. Place all in `com.valueinvesting.demo.dto`

## Task Group 2 — DemoAnalysisService

4. Create `DemoAnalysisService` (`@Service`) in `com.valueinvesting.demo`
5. Inject `YahooFinanceClient`, `YahooFinanceAdapter`, `DcfCalculator`, `GrahamCalculator`, `MarginOfSafetyCalculator`
6. Implement `analyze(String symbol) → DemoAnalysisResponse`:
   - Call `YahooFinanceClient` → get raw Yahoo DTOs
   - Call adapter → produce `FundamentalSnapshot` + `RatioSnapshot` + current price
   - Run `GrahamCalculator.calculate(eps, bvps)`
   - Check DCF eligibility (RULE-06: ≥ 3 years positive FCF); if eligible run `DcfCalculator`
   - Compute composite fair value (DCF 60% / Graham 40% if DCF available, else 100% Graham)
   - Run `MarginOfSafetyCalculator.compute(composite, currentPrice)`
   - Map MoS to `Recommendation` enum
   - Build and return `DemoAnalysisResponse`
7. Propagate Yahoo errors as typed exceptions:
   - Symbol not found → `SymbolNotFoundException` (mapped to 404)
   - Yahoo unreachable → `MarketDataUnavailableException` (mapped to 503)

## Task Group 3 — Controller & Error Handling

8. Create `DemoAnalysisController` (`@RestController`) at `/demo/analyze/{symbol}`
9. Create `@RestControllerAdvice` (or use `@ExceptionHandler` in the controller) mapping:
   - `SymbolNotFoundException` → 404 `{ "error": "Symbol not found: {symbol}" }`
   - `MarketDataUnavailableException` → 503 `{ "error": "Market data unavailable, please retry later." }`
10. Add `/demo/**` to Spring Security permit-all list (no auth required)

## Task Group 4 — Tests

11. Write `DemoAnalysisControllerTest` using `@SpringBootTest` + `MockMvc`:
    - Happy path: mock `YahooFinanceClient`, assert 200 + JSON fields present
    - DCF-skipped path: mock client returning < 3 years FCF, assert `valuation.dcf` is null
    - 404 path: mock client throwing `SymbolNotFoundException`
    - 503 path: mock client throwing `MarketDataUnavailableException`
