# Requirements — Phase C2: DCF Engine

## Context

Phase C1 delivered `GrahamCalculator` and `DdmCalculator` as pure-math units with full test coverage. C2 closes out the three valuation calculators by adding the Discounted Cash Flow (DCF) engine — the highest-weight component in the composite fair value formula (60%). C3 will wire all three into `ValuationService` and persist results to DB.

This phase follows the roadmap spec exactly; no deviations were requested.

Reference: `specs/roadmap.md` → Group C, Phase C2.

---

## Scope

All four elements below are in scope for this phase:

### 1. `DcfCalculator`

A pure-Java calculator class with a single public method:

```java
DcfResult calculate(DcfInput input)
```

**`DcfInput` fields:**

| Field | Type | Description |
|---|---|---|
| `fcfTtm` | `BigDecimal` | Trailing twelve-month free cash flow |
| `growthY1Y5` | `BigDecimal` | Annual FCF growth rate for years 1–5 |
| `growthY6Y10` | `BigDecimal` | Annual FCF growth rate for years 6–10 |
| `terminalRate` | `BigDecimal` | Perpetual growth rate (terminal value) |
| `wacc` | `BigDecimal` | Weighted average cost of capital (discount rate) |
| `shares` | `BigDecimal` | Shares outstanding |
| `netDebt` | `BigDecimal` | Net debt (subtracted from enterprise value) |

**`DcfResult` fields:**

| Field | Type | Description |
|---|---|---|
| `fairValue` | `BigDecimal` | Intrinsic value per share (base scenario) |
| `fairValueLow` | `BigDecimal` | Bear scenario: WACC + 2% |
| `fairValueHigh` | `BigDecimal` | Bull scenario: WACC − 1% |
| `enterpriseValue` | `BigDecimal` | PV of FCF stream + terminal value before net-debt adjustment |
| `parameters` | `DcfInput` | Snapshot of the input used — required for transparency (Design Principle 2) |

**Formula:**

Two-stage DCF:
1. Discount FCF for years 1–5 using `growthY1Y5` and `wacc`
2. Discount FCF for years 6–10 using `growthY6Y10` and `wacc`
3. Terminal value = `FCF_Y10 * (1 + terminalRate) / (wacc - terminalRate)`; discount to present
4. Enterprise value = sum of all PV cash flows + PV of terminal value
5. Equity value = Enterprise value − net debt
6. Fair value per share = Equity value / shares

For low/high scenarios, re-run the full calculation with `wacc + 0.02` and `wacc - 0.01` respectively.

### 2. RULE-06 Guard

```java
if (input.positiveFreeCashFlowYears() < 3) {
    throw new InsufficientDataException("DCF requires at least 3 years of positive FCF");
}
```

`DcfInput` must carry or derive `positiveFreeCashFlowYears` (count of years in the FCF history where FCF > 0). The guard fires before any calculation. This is consistent with the guard referenced in phases Z3 and Val1.

### 3. Unit Tests

- At least one reference-value test using AAPL-like hardcoded inputs that asserts `fairValue`, `fairValueLow`, `fairValueHigh`, and `enterpriseValue` to a known expected value (delta tolerance: ±0.01).
- Tests for RULE-06 guard: throws `InsufficientDataException` when `positiveFreeCashFlowYears < 3`; does not throw when exactly 3.
- Edge case: `terminalRate >= wacc` — must throw `IllegalArgumentException` (Gordon Growth Model denominator would be zero or negative).

### 4. Wiring to `ValuationService`

- `DcfCalculator` is injectable (Spring `@Component` or constructed via constructor injection in `ValuationService`).
- `ValuationService` (introduced in C3 as the orchestrator) can call `DcfCalculator.calculate()` directly.
- No endpoint or DB persistence is introduced in C2 — that is C3's job.

---

## Out of Scope

- `ValuationService.calculate()` orchestration → C3
- `MarginOfSafetyCalculator` → C3
- DB persistence of `ValuationResult` → C3
- REST endpoint → C3
- Mid-term growth phases (Y11+) — not in spec, not added here

---

## Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Scenario band widths | WACC+2% (low), WACC−1% (high) | Roadmap spec; conservative defaults per Design Principle 3 |
| Rounding | `HALF_UP`, 2 decimal places for per-share values | Consistent with C1 calculators |
| `DcfInput` record type | Java `record` | Immutable; consistent with `FundamentalSnapshot` pattern in the project |
| Exception type for RULE-06 | `InsufficientDataException` (existing class from C1/DDM guard) | Reuse; consistent with `DdmCalculator` guard for < 5 dividend years |
| Exception for invalid terminal rate | `IllegalArgumentException` | Standard Java; no domain exception needed for an input validation error |
