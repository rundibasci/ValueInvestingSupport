# Plan — Phase C3: Composite Fair Value & Margin of Safety

## Task Group 1 — Weight Configuration

**1.1** Create `ValuationWeightsProperties` record in `it.mazzoni.vis.valuation`:
```java
@ConfigurationProperties("valuation.weights")
public record ValuationWeightsProperties(
    BigDecimal dcf,
    BigDecimal graham,
    BigDecimal ddm
) {}
```

**1.2** Register with `@EnableConfigurationProperties(ValuationWeightsProperties.class)` on a
`@Configuration` class (or the main app class).

**1.3** Add defaults to `application.yml`:
```yaml
valuation:
  weights:
    dcf: 0.60
    graham: 0.25
    ddm: 0.15
```

---

## Task Group 2 — Repository Queries

Two new query methods are needed (add to existing repositories or create dedicated ones):

**2.1** On `FundamentalSnapshotRepository`:
- `findTopBySecurityAndPeriodOrderByReportDateDesc(Security, Period)` — latest TTM or ANNUAL snapshot
- `findTop3BySecurityAndPeriodOrderByReportDateDesc(Security, Period.ANNUAL)` — last 3 annual for FCF count

**2.2** On `DividendRecordRepository`:
- `findBySecurityAndExDividendDateAfterOrderByExDividendDateDesc(Security, LocalDate)` — last 10 years of records

**2.3** On `PriceQuoteRepository`:
- `findTopBySecurityOrderByQuoteDateDesc(Security)` — most recent close price

---

## Task Group 3 — ValuationService

Create `it.mazzoni.vis.valuation.ValuationService` (Spring `@Service`).

**3.1** Method signature:
```java
public ValuationResult calculate(String symbol, ValuationParams params)
```

**3.2** Resolve `Security` entity by `symbol` (case-insensitive) — throw `SecurityNotFoundException`
(maps to 404) if absent.

**3.3** Load fundamental data:
- Fetch latest TTM snapshot; fall back to latest ANNUAL if TTM absent.
- Throw `ValuationDataUnavailableException` (422) if neither exists.
- Compute: `bvps = totalEquity / sharesOutstanding`, `netDebt = totalDebt - cash`.
- Use `epsDiluted` (conservative).

**3.4** Count FCF positive years:
- Load last 3 ANNUAL snapshots; count those with `freeCashFlow > 0`.

**3.5** Run **Graham**:
```java
BigDecimal grahamNumber;
try {
    grahamNumber = GrahamCalculator.calculate(snapshot.getEpsDiluted(), bvps);
} catch (GrahamNotApplicableException e) {
    grahamNumber = null;
}
```

**3.6** Run **DCF**:
```java
Optional<DcfResult> dcfResult = new DcfCalculator().calculate(
    new DcfInput(fcfTtm, fcfYearsPositive, params.growthY1Y5(), params.growthY6Y10(),
                 params.terminalRate(), params.wacc(), sharesAsBigDecimal, netDebt)
);
```
`dcfFairValue` = `dcfResult.map(DcfResult::fairValue).orElse(null)`.

**3.7** Run **DDM** (only if `params.requiredReturn()` and `params.dividendGrowthRate()` are non-null):
- Query `DividendRecord` for the last 10 years; sum amounts in the last 12 months → `dpsTtm`.
- Count consecutive calendar years with ≥ 1 payment looking back from today → `consecutiveDividendYears`.
- Call `DdmCalculator.calculate(dpsTtm, params.dividendGrowthRate(), params.requiredReturn(), consecutiveDividendYears)`.
- Catch `DdmNotEligibleException` and `DdmNotApplicableException` → `ddmFairValue = null`.

**3.8** Compute **composite fair value**:
- Build an availability map `{ dcf → dcfFairValue, graham → grahamNumber, ddm → ddmFairValue }` keeping only non-null entries.
- If map is empty → throw `ValuationNotApplicableException` (422).
- Compute effective weights by normalizing configured weights over available models only.
- `compositeFairValue = sum(weight_i * fairValue_i)`.

**3.9** Load **current price**:
- Most recent `PriceQuote.close` for the symbol. Null if no price quote exists (MoS will be null).

**3.10** Compute **MoS** and **Recommendation**:
- `MoS = MarginOfSafetyCalculator.compute(compositeFairValue, currentPrice)` (already implemented).
- Recommendation thresholds as defined in `requirements.md`.

**3.11** Persist and return:
- Build `ValuationResult` entity (security, valuationDate=today, dcfFairValue, dcfFairValueLow,
  dcfFairValueHigh, grahamNumber, ddmFairValue, compositeFairValue, currentPrice, marginOfSafety,
  recommendation, source="fmp").
- `valuationResultRepository.save(result)` and return the saved entity.

---

## Task Group 4 — REST Endpoint

**4.1** Create `it.mazzoni.vis.api.ValuationController`:
```
POST /api/v1/securities/{symbol}/valuation/dcf
```
Auth required (all roles). Delegates to `ValuationService.calculate()`.

**4.2** Create `ValuationRequest` record (request body):
```java
public record ValuationRequest(
    @NotNull @DecimalMin("0.01") BigDecimal wacc,
    @NotNull BigDecimal growthY1Y5,
    @NotNull BigDecimal growthY6Y10,
    @NotNull @DecimalMin("0.001") BigDecimal terminalRate,
    BigDecimal requiredReturn,
    BigDecimal dividendGrowthRate
) {}
```
Map to `ValuationParams` inside the controller.

**4.3** Create `ValuationResponse` record (response body) — mirrors `requirements.md` JSON structure,
including `weights` map with effective (normalized) weights used.

**4.4** `ValuationParams` value record passed from controller → service (holds validated inputs from request).

**4.5** Exception → HTTP status mapping (via `@ControllerAdvice` or existing handler):
- `SecurityNotFoundException` → 404
- `ValuationDataUnavailableException` → 422
- `ValuationNotApplicableException` → 422

---

## Task Group 5 — Tests

**5.1** `ValuationServiceTest` (unit, mocked repos):
- Happy path: DCF + Graham + DDM all eligible → correct weighted average
- DDM-absent path: Graham + DCF → normalized weights applied
- DCF-absent path: FCF positive years < 3 → Graham-only composite
- All-absent path: → `ValuationNotApplicableException` thrown
- Graham-absent (negative EPS): → DCF + DDM composite (or DCF-only if DDM absent too)

**5.2** `ValuationControllerTest` (MockMvc, mocked `ValuationService`):
- 200 OK with full response body on valid request
- 404 when service throws `SecurityNotFoundException`
- 422 when service throws `ValuationNotApplicableException`
- 400 on missing required field (`wacc` absent)

**5.3** Reference value check (in `ValuationServiceTest` or a dedicated `CompositeWeightTest`):
- Given: dcfFairValue=210.00, grahamNumber=148.32, ddmFairValue=null
- Configured weights: dcf=0.60, graham=0.25, ddm=0.15
- Effective: dcf=0.7059, graham=0.2941
- Expected composite: `210.00 * 0.7059 + 148.32 * 0.2941 ≈ 192.10` (verify to 2dp)
