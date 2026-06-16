# Plan — Phase C1: Graham Number & DDM

## Task Group 1 — Exception Classes

1.1 Create `GrahamNotApplicableException` (extends `RuntimeException`) in `valuation.exception` package  
1.2 Create `DdmNotEligibleException` (extends `RuntimeException`) in `valuation.exception` package  
1.3 Create `DdmNotApplicableException` (extends `RuntimeException`) in `valuation.exception` package  

## Task Group 2 — GrahamCalculator

2.1 Create `GrahamCalculator` class in `valuation.calculator` package  
2.2 Implement `calculate(BigDecimal eps, BigDecimal bvps) → BigDecimal`:
- Validate `eps > 0` and `bvps > 0`; throw `GrahamNotApplicableException` if either fails
- Apply formula: `√(22.5 × eps × bvps)` using `BigDecimal.sqrt(MathContext.DECIMAL128)`
- Round result to 2dp `HALF_UP` before returning

## Task Group 3 — DdmCalculator

3.1 Create `DdmCalculator` class in `valuation.calculator` package  
3.2 Implement `calculate(BigDecimal dpsTtm, BigDecimal dividendGrowthRate, BigDecimal requiredReturn, int consecutiveDividendYears) → BigDecimal`:
- RULE-07 guard first: if `consecutiveDividendYears < 5`, throw `DdmNotEligibleException`
- Guard: if `requiredReturn <= dividendGrowthRate`, throw `DdmNotApplicableException`
- Apply formula: `dpsTtm / (requiredReturn − dividendGrowthRate)`
- Round result to 2dp `HALF_UP` before returning

## Task Group 4 — Unit Tests: GrahamCalculatorTest

4.1 Happy path — AAPL-era inputs: `eps = 6.11`, `bvps = 4.25` → verify result equals `√(22.5 × 6.11 × 4.25)` to 2dp  
4.2 Happy path — round numbers: `eps = 5.00`, `bvps = 30.00` → expected `√3375 ≈ 58.09`  
4.3 Guard — zero EPS: `calculate(0, 30)` → `GrahamNotApplicableException`  
4.4 Guard — negative BVPS: `calculate(5, -1)` → `GrahamNotApplicableException`  

## Task Group 5 — Unit Tests: DdmCalculatorTest

5.1 Happy path — KO-era inputs: `dpsTtm = 1.84`, `growthRate = 0.05`, `requiredReturn = 0.08`, `consecutiveDividendYears = 60` → expected `1.84 / 0.03 = 61.33`  
5.2 Happy path — round numbers: `dpsTtm = 2.00`, `growthRate = 0.04`, `requiredReturn = 0.09`, `years = 10` → expected `2.00 / 0.05 = 40.00`  
5.3 RULE-07 guard — ineligible: `consecutiveDividendYears = 4` → `DdmNotEligibleException`  
5.4 Guard — requiredReturn equals growthRate: `requiredReturn = 0.05`, `growthRate = 0.05` → `DdmNotApplicableException`  
5.5 Guard — requiredReturn less than growthRate: `requiredReturn = 0.04`, `growthRate = 0.05` → `DdmNotApplicableException`  
