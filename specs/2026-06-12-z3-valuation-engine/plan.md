# Plan — Phase Z3: Valuation Engine (Demo-compatible)

## Task Group 1 — Domain Value Objects

1. Create `DcfInput` Java record in `valuation` package:
   - Fields: `fcfTtm`, `growthY1Y5`, `growthY6Y10`, `terminalRate`, `wacc`, `shares`, `netDebt` (all `BigDecimal`), `fcfYearsPositive` (`int`)
2. Create `DcfResult` Java record:
   - Fields: `fairValue`, `fairValueLow`, `fairValueHigh`, `enterpriseValue` (all `BigDecimal`), `parameters` (snapshot of `DcfInput`)

## Task Group 2 — Graham Calculator

3. Create `GrahamCalculator` class with static method `calculate(BigDecimal eps, BigDecimal bvps) → BigDecimal`
4. Guard: return `null` if either input is null, ≤ 0
5. Formula: `sqrt(22.5 × eps × bvps)` using `BigDecimal` math (scale 2, HALF_UP)

## Task Group 3 — DCF Calculator

6. Create `DcfCalculator` class with method `calculate(DcfInput input) → Optional<DcfResult>`
7. RULE-06 guard: return `Optional.empty()` if `input.fcfYearsPositive() < 3`
8. Implement two-stage DCF formula:
   - Stage 1: FCF growth years 1–5 at `growthY1Y5`, discounted at `wacc`
   - Stage 2: FCF growth years 6–10 at `growthY6Y10`, discounted at `wacc`
   - Terminal value: year-10 FCF × `(1 + terminalRate) / (wacc − terminalRate)`, discounted back
   - Enterprise value = sum of stages + terminal
   - Equity value = enterprise value − `netDebt`
   - Fair value per share = equity value / `shares`
9. Compute `fairValueLow` by repeating with `wacc + 0.02`
10. Compute `fairValueHigh` by repeating with `wacc - 0.01`

## Task Group 4 — Margin of Safety Calculator

11. Create `MarginOfSafetyCalculator` class with static method `compute(BigDecimal fairValue, BigDecimal currentPrice) → BigDecimal`
12. Guard: return `null` if either input is null or `fairValue` ≤ 0
13. Formula: `(fairValue − currentPrice) / fairValue × 100`, scale 2, HALF_UP

## Task Group 5 — Unit Tests

14. `GrahamCalculatorTest`: round-number cases (EPS=5, BVPS=20 → expected `√(22.5×5×20)` = `√2250` ≈ `47.43`), null/zero guard cases
15. `DcfCalculatorTest`: round-number DCF case verifying base/low/high fair values, RULE-06 guard returns empty when `fcfYearsPositive < 3`
16. `MarginOfSafetyCalculatorTest`: positive MoS (fairValue=100, price=85 → 15.00%), negative MoS (price > fairValue), null guard
