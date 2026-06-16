# Plan — Phase C2: DCF Engine

## Task Groups

### 1. Domain types

1.1 Create `DcfInput` Java record with fields: `fcfTtm`, `growthY1Y5`, `growthY6Y10`, `terminalRate`, `wacc`, `shares`, `netDebt`, `positiveFreeCashFlowYears`.

1.2 Create `DcfResult` Java record with fields: `fairValue`, `fairValueLow`, `fairValueHigh`, `enterpriseValue`, `parameters` (snapshot of `DcfInput`).

---

### 2. Guard and calculator

2.1 Create `DcfCalculator` as a Spring `@Component`. Implement RULE-06 guard as the first statement in `calculate()` — throw `InsufficientDataException` if `positiveFreeCashFlowYears < 3`.

2.2 Implement the two-stage DCF math for the base scenario (years 1–5, years 6–10, terminal value, enterprise value, equity value, per-share value).

2.3 Add guard for `terminalRate >= wacc` — throw `IllegalArgumentException`.

2.4 Compute `fairValueLow` by re-running the calculation with `wacc + 0.02`.

2.5 Compute `fairValueHigh` by re-running the calculation with `wacc - 0.01`.

2.6 Return `DcfResult` with all five fields populated.

---

### 3. Unit tests

3.1 Write a reference-value test: AAPL-like inputs → assert `fairValue`, `fairValueLow`, `fairValueHigh`, `enterpriseValue` to ±0.01. (Derive the expected values by hand or spreadsheet before coding the test.)

3.2 Write RULE-06 guard test: `positiveFreeCashFlowYears = 2` → `InsufficientDataException` thrown.

3.3 Write RULE-06 boundary test: `positiveFreeCashFlowYears = 3` → no exception, result returned.

3.4 Write terminal-rate guard test: `terminalRate >= wacc` → `IllegalArgumentException`.

---

### 4. Integration check

4.1 Verify `DcfCalculator` is picked up by the Spring context in the `test` profile (a simple `@SpringBootTest` context-load test, or confirm it is already covered by an existing smoke test).

4.2 Confirm `InsufficientDataException` is the same class used by `DdmCalculator` in C1 — no duplicate introduced.

---

### 5. Spec wrap-up

5.1 Update `specs/roadmap.md` (or a changelog entry) to mark C2 complete if that is the project convention.

5.2 Open PR against `main` with a description linking to `specs/2026-06-16-c2-dcf-engine/`.
