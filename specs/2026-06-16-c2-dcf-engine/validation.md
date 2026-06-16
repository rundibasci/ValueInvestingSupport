# Validation — Phase C2: DCF Engine

## Definition of Done

C2 is ready to merge when **all** of the following are true.

---

### 1. Unit tests green

Run:
```
mvn test -pl backend -Dtest="DcfCalculatorTest"
```

All tests in `DcfCalculatorTest` (or equivalent) must pass with no failures or skips.

---

### 2. Reference-value test passes

At least one test asserts the two-stage DCF formula produces the correct per-share fair value for a known set of AAPL-like inputs.

Acceptance criteria:
- `fairValue` matches the hand-calculated expected value within ±0.01
- `fairValueLow` equals the result when `wacc + 0.02` is used (within ±0.01)
- `fairValueHigh` equals the result when `wacc - 0.01` is used (within ±0.01)
- `enterpriseValue` matches the pre-debt-adjustment sum (within ±0.01)
- `parameters` field on the result is the same object as the input (snapshot preserved)

---

### 3. RULE-06 guard enforced

- `positiveFreeCashFlowYears = 2` → `InsufficientDataException` is thrown, no result returned
- `positiveFreeCashFlowYears = 3` → result returned normally (boundary passes)

---

### 4. Terminal-rate guard enforced

- `terminalRate >= wacc` → `IllegalArgumentException` is thrown before any calculation

---

### 5. No regressions in C1 tests

Run:
```
mvn test -pl backend -Dtest="GrahamCalculatorTest,DdmCalculatorTest"
```

C1 tests must remain fully green — no shared code broken by C2 changes.

---

### 6. `InsufficientDataException` is not duplicated

Confirm via grep or code review that C2 reuses the same `InsufficientDataException` class introduced in C1, not a new copy.

---

### 7. Spring context loads with `DcfCalculator` present

Either an existing smoke/context test passes, or a minimal `@SpringBootTest` confirms the application context starts without errors in the `test` profile.

---

## Out of Scope for This Validation

- No REST endpoint to call — that is C3.
- No DB persistence check — that is C3.
- No FMP integration or live data — that is Val1/Val2.
