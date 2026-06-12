# Validation — Phase Z3: Valuation Engine (Demo-compatible)

## Definition of Done

Phase Z3 is complete and ready to merge when **all** of the following are true.

## 1. Build passes

```
mvn clean verify
```
No compilation errors, no test failures.

## 2. Unit test coverage — calculators

| Test class | Cases required |
|---|---|
| `GrahamCalculatorTest` | Happy path with round numbers; null EPS guard; null BVPS guard; zero EPS guard; negative BVPS guard |
| `DcfCalculatorTest` | Happy path (base/low/high fair values all present); RULE-06 guard returns `Optional.empty()` when `fcfYearsPositive < 3`; RULE-06 passes when `fcfYearsPositive == 3` |
| `MarginOfSafetyCalculatorTest` | Positive MoS; negative MoS (overvalued); zero MoS; null fairValue guard; zero fairValue guard |

All tests must assert exact `BigDecimal` values (scale 2, HALF_UP) — no floating-point comparisons.

## 3. Formula correctness spot-checks

Verify by hand before merge:

| Calculator | Inputs | Expected output |
|---|---|---|
| Graham | EPS=5, BVPS=20 | `√(22.5 × 5 × 20)` = `√2250` ≈ **47.43** |
| MoS | fairValue=100, price=85 | `(100−85)/100 × 100` = **15.00 %** |
| MoS | fairValue=100, price=110 | `(100−110)/100 × 100` = **−10.00 %** |

## 4. RULE-06 guard is non-throwing

Confirm that calling `DcfCalculator.calculate(input)` with `fcfYearsPositive = 2` returns `Optional.empty()` and does **not** throw any exception. A thrown exception here would break the Z4 endpoint.

## 5. No persistence / HTTP dependencies

Calculators must have zero Spring annotations, zero JPA/repository imports, and zero WebClient/HTTP imports. They must be plain Java classes instantiable with `new` in tests — no Spring context required.

## 6. Package placement

All classes under `com.valueinvesting.valuation` (or matching existing package structure from Z1/Z2). No calculator logic in controller or adapter packages.

## Merge criteria

- All items above checked off
- No TODO comments left in calculator or test source files
- `mvn clean verify` green on a clean checkout (no local state required)
