# MA3 Moat Data Depth & Derived ROIC Fallback Plan

1. Confirm existing data paths and contracts
   - Trace FMP and Yahoo annual income-statement, balance-sheet, and ratio mappings into persisted snapshots.
   - Document current moat-series selection, normalization, classification, API DTOs, review composition, and MA2 rendering.
   - Identify snapshot identity/idempotency rules and choose a normalized persistence shape for annual ROIC provenance.

2. Extend annual financial persistence
   - Add nullable historical fields for equity, debt, cash, EBIT/operating income, tax inputs, and available invested-capital diagnostics.
   - Add an annual ROIC observation model containing value, source, method/formula metadata, input provider, and structured unavailability information.
   - Add equivalent PostgreSQL and H2 Flyway migrations, entities, repositories, indexes, and repository tests.

3. Deepen provider mapping and ingestion
   - Extend FMP DTOs/adapters to map the required annual financial fields without leaking raw payloads beyond the client layer.
   - Extend Yahoo mapping where equivalent inputs are available and preserve explicit source context.
   - Integrate persistence into seed and scheduled ingestion flows while retaining immutable history and idempotent replay behavior.

4. Implement deterministic derived ROIC
   - Add a focused calculator for NOPAT, invested capital, average invested capital, and derived ROIC using BigDecimal arithmetic.
   - Validate and normalize tax rates, percentage formats, denominators, and missing inputs; return structured unavailability instead of guesses.
   - Persist formula version/notes and whether average or single-period invested capital was used.

5. Apply provider precedence and fallback
   - Select provider history when at least five valid provider annual observations exist.
   - When provider history is insufficient, build eligible derived observations from persisted annual fundamentals and retain source per point.
   - Update `MoatAssessmentService` to classify only when the selected series contains at least five valid annual observations.
   - Keep true insufficient-data behavior and explanatory availability messages intact.

6. Extend API and review contracts
   - Add per-year value, source, provider/input context, formula note, and unavailable reason to the moat response.
   - Include the same provenance in the security review packet without removing established MA1 fields.
   - Add a methodology/disclaimer field or stable frontend copy contract that explains internal derivation and the decision-support boundary.

7. Show provenance in the frontend
   - Update TypeScript API types and review-page moat components for the expanded series.
   - Show provenance for every annual point, including unavailable years, with accessible labels and a visible derived-method disclaimer.
   - Preserve the existing ROIC-versus-WACC chart and moat summary while ensuring derived values are never styled as provider facts.

8. Add automated regression coverage
   - Test provider-history precedence with at least five valid provider points.
   - Test derived fallback with fewer than five provider points and at least five valid fundamental years.
   - Test missing fields, invalid tax inputs, non-positive invested capital, and fewer than five total observations.
   - Test ingestion idempotency, source persistence, API/review serialization, and frontend provenance rendering.
   - Add an `INGR` regression fixture or controlled integration path that does not require a live provider call in the default test suite.

9. Verify and prepare merge evidence
   - Run targeted backend moat, ingestion, repository, controller, and review tests, followed by the backend suite.
   - Run targeted frontend tests, TypeScript checking, and the production build.
   - If a configured FMP integration profile is available, re-ingest `INGR` and capture the resulting multi-year provenance as optional live evidence.
   - Run `git diff --check`, review migrations and API compatibility, and record any environment-limited checks before merge.
