# Validation — B1: Market Data Client Abstraction

## Definition of Done

B1 is complete and ready to merge when all of the following pass:

---

## 1. Tests green, no live network

```
mvn test -Dtest="YahooMarketDataClientTest,FmpMarketDataClientTest"
```

- All assertions pass
- No outbound HTTP connections during the test run (WireMock intercepts everything)
- Zero test failures, zero errors

---

## 2. Retry logic verified

`FmpMarketDataClientTest` includes a dedicated retry scenario:
- WireMock stub for `/profile/RETRY` returns 429 on the first two calls, 200 on the third
- Test asserts the successful domain object is returned
- WireMock request journal confirms exactly **3 calls** were made to that URL

---

## 3. Client selection by env var

Start the application twice in isolation:

```bash
MARKET_DATA_SOURCE=yahoo mvn spring-boot:run
# curl http://localhost:8080/actuator/health → {"status":"UP"}

MARKET_DATA_SOURCE=fmp FMP_API_KEY=placeholder mvn spring-boot:run
# curl http://localhost:8080/actuator/health → {"status":"UP"}
```

Both profiles start without error. Only one `MarketDataClient` bean exists in each context (verify with `actuator/beans` if needed).

---

## 4. No DB writes in B1 code

Grep confirms no `save()`, `saveAll()`, `persist()`, or `EntityManager` calls exist in any class under the `marketdata` package:

```bash
grep -r "\.save\|\.saveAll\|\.persist\|EntityManager" src/main/java/com/valueinvesting/marketdata/
# Expected: no matches
```

---

## 5. Interface contract enforced

Both `YahooMarketDataClient` and `FmpMarketDataClient` implement `MarketDataClient`. The compiler rejects a build where either implementation is missing any of the four interface methods.

---

## 6. Error taxonomy correct

| Upstream condition | Exception thrown | HTTP status returned |
|---|---|---|
| Symbol not found (Yahoo 404 / FMP 404) | `MarketDataException(NOT_FOUND)` | 404 |
| Provider unavailable / retries exhausted | `MarketDataException(SERVICE_UNAVAILABLE)` | 503 |

Verified by WireMock error-path tests in steps 4.3 and 4.4 of the plan.

---

## Merge checklist

- [ ] `mvn clean test` passes (all modules)
- [ ] No `@Disabled` or `@Ignore` on any B1 test
- [ ] `MARKET_DATA_SOURCE` is documented in `.env.example`
- [ ] No hardcoded FMP API key in any source file (`grep -r "FMP_API_KEY" src/main/` returns no string literals)
- [ ] PR description references this validation doc
