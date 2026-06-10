# Validation — Z2: Yahoo Finance Client

## Definition of Done

Z2 is complete and ready to merge when all of the following pass.

---

## 1. Build & Tests

- [ ] `mvn verify` (default profile, no `integration` tag) exits 0
- [ ] All unit tests in `YahooDtoDeserializationTest` pass — every DTO record deserializes from
      the AAPL fixture without error
- [ ] All unit tests in `YahooFinanceAdapterTest` pass, including the null-field resilience case
      (stripped cashflow fixture → empty FCF list, no NPE)
- [ ] No compiler warnings about unchecked operations or missing nullability annotations

---

## 2. Correct Domain Mapping (Spot Checks)

Using the committed AAPL JSON fixtures:

| Field | Source path in Yahoo JSON | Expected behaviour |
|---|---|---|
| `currentPrice` | `financialData.currentPrice.raw` | Non-null positive `BigDecimal` |
| `bookValuePerShare` | `defaultKeyStatistics.bookValue.raw` | Non-null positive `BigDecimal` |
| `eps` (TTM) | `defaultKeyStatistics.trailingEps.raw` | Non-null, can be negative |
| `revenue` history | `incomeStatementHistory.incomeStatementHistory[*].totalRevenue.raw` | List of up to 4 entries |
| `fcf` history | `cashflowStatementHistory[*]`: `totalCashFromOperations.raw - capitalExpenditures.raw` | List of up to 4 entries |
| `sector` | `assetProfile.sector` | Non-blank string |
| `peRatio` | `summaryDetail.trailingPE.raw` | Non-null, or null if not reported |
| `dividendYield` | `summaryDetail.dividendYield.raw` | Non-null, or null if company pays no dividend |

---

## 3. Caching

- [ ] A second call to `YahooFinanceClient.getQuoteSummary("AAPL")` within 15 min hits the
      Caffeine cache and does NOT produce a second HTTP request (verify with a `@SpyBean` or
      Caffeine cache stat assertion in a unit test)

---

## 4. Error Handling

- [ ] A `quoteSummary` response containing `"No fundamentals data found"` causes
      `SymbolNotFoundException` to be thrown — test with a stubbed WebClient response
- [ ] A 503 response from Yahoo Finance causes `MarketDataUnavailableException` — test with
      a stubbed WebClient response

---

## 5. Integration Test (Optional / Opt-In)

- [ ] `mvn verify -Dgroups=integration` passes against live Yahoo Finance for AAPL
- [ ] Test is annotated `@Tag("integration")` and is excluded from default `mvn verify` run
- [ ] If Yahoo Finance is unreachable, the test fails with a clear network error message (not a
      NullPointerException from unmapped fields)

---

## 6. Readiness for Z3

- [ ] `FundamentalSnapshot` contains: `currentPrice`, `eps` (TTM), `bookValuePerShare`,
      `fcf` (list, ≥ 0 entries), and enough fields for `GrahamCalculator` and `DcfCalculator`
- [ ] `RatioSnapshot` contains: `peRatio`, `roic`, `roe`, `currentRatio`, `debtToEquity`,
      `dividendYield`
- [ ] No Z3 code has been written yet, but a `YahooFinanceAdapter` call from a Z3 integration
      test would produce non-null snapshots for AAPL
