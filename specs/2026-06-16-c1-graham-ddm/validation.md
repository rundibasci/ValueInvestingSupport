# Validation — Phase C1: Graham Number & DDM

## Definition of Done

Phase C1 is complete and ready to merge when all of the following are true.

## 1. Tests Pass

```
./mvnw test -pl backend -Dtest="GrahamCalculatorTest,DdmCalculatorTest"
```

- `GrahamCalculatorTest`: all 4 cases green (2 happy-path, 2 guard)
- `DdmCalculatorTest`: all 5 cases green (2 happy-path, 3 guard)
- Zero test failures, zero errors

## 2. Formula Correctness

Manually verify reference values used in tests:

| Test | Formula | Expected |
|---|---|---|
| Graham (EPS=5, BVPS=30) | √(22.5 × 5 × 30) = √3375 | 58.09 |
| DDM (DPS=2, g=4%, r=9%) | 2 / (0.09 − 0.04) | 40.00 |
| DDM (DPS=1.84, g=5%, r=8%) | 1.84 / (0.08 − 0.05) | 61.33 |

## 3. Guard Behaviour

- `GrahamCalculator.calculate(0, 30)` → `GrahamNotApplicableException` (not a generic exception)
- `DdmCalculator.calculate(1.84, 0.05, 0.08, 4)` → `DdmNotEligibleException` (RULE-07)
- `DdmCalculator.calculate(1.84, 0.05, 0.05, 10)` → `DdmNotApplicableException` (zero denominator)

## 4. Domain Isolation

- `grep -r "FundamentalSnapshot\|RatioSnapshot\|@Entity\|@Repository" backend/src/main/java/*/valuation/calculator/` returns no matches
- No Spring `@Autowired` or `@Component` dependencies in calculator classes (or, if `@Component`, no injected beans)

## 5. Build Clean

```
./mvnw verify -pl backend -DskipITs
```

Zero compilation errors, zero Checkstyle/SpotBugs violations if configured.

## Merge Criteria

- All items above confirmed ✓
- PR description references this validation checklist
- No unrelated files changed (scope: `valuation/calculator/`, `valuation/exception/`, test classes only)
