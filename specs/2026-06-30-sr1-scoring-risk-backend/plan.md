# Phase SR1 Plan - Scoring & Risk Backend

1. Extend score persistence and configuration.
   - Add a Flyway migration for ValueScore gate/profile fields plus Piotroski, Altman, cyclicality, and earnings-quality result tables.
   - Add JPA entities and repositories for persisted risk results.
   - Add configurable sector weight profiles under `scoring.risk.weight-profiles` in `application.yml`.

2. Update ValueScore computation.
   - Apply RULE-09: negative margin of safety caps total ValueScore at 40.
   - Persist whether the gate was applied, the raw score before capping, and the applied sector weight profile.
   - Use sector-adaptive weight allocation while preserving the existing 100-point score contract.

3. Add backend risk services and endpoints.
   - Add Piotroski computation with 9 factor pass/fail details from persisted fundamentals/ratios.
   - Add Altman Z-Score computation with manufacturing and non-manufacturing variants.
   - Add cyclicality classification from annual revenue and earnings volatility.
   - Add earnings quality classification from FCF/net income and Sloan accruals.
   - Expose authenticated endpoints under `/api/v1/securities/{symbol}` for each result.

4. Extend screener filters.
   - Add `piotroskiMin`, `piotroskiMax`, and `altmanZone` request filters.
   - Filter against latest persisted results and keep local-DB-only screener behavior.

5. Test and validate.
   - Add unit tests for MoS gate, each weight profile category, Piotroski factors, Altman variants, cyclicality thresholds, earnings quality/accruals, endpoint DTOs, and screener filters.
   - Run backend unit/integration tests with Maven.

