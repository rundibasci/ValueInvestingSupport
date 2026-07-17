# MA3 Moat Data Depth & Derived ROIC Fallback Validation

## Functional Acceptance

- A security with five or more valid annual provider ROIC observations continues to use provider history and does not replace it with derived values.
- A security with fewer than five valid provider ROIC observations and at least five eligible annual fundamental records receives a reproducible derived series and a moat classification.
- `INGR` no longer remains provider-limited after re-ingestion when its persisted financial inputs support at least five derived observations.
- A security without five provider or derived observations remains `INSUFFICIENT_DATA`; no missing inputs are fabricated.
- Re-ingestion persists deeper annual fields and ROIC observations idempotently under existing snapshot identity rules.
- Moat and review responses expose provenance, methodology, and availability for every annual observation.
- The review page visibly distinguishes `FMP_RATIO`, `FMP_KEY_METRIC`, `DERIVED_INTERNAL`, and `UNAVAILABLE` observations and shows the approved disclaimer.
- Existing moat charts, classifications, screener behavior, review fields, authentication, and decision-support language remain intact.

## Calculation Checks

- `NOPAT` equals EBIT multiplied by one minus the validated effective tax rate or documented conservative proxy.
- Invested capital equals total equity plus total debt minus cash and cash equivalents.
- Average opening/closing invested capital is used when both periods exist, with explicit metadata.
- Any permitted single-period denominator is explicitly labeled and follows the conservative eligibility rule selected during implementation.
- Percentage-versus-decimal normalization is deterministic.
- Missing EBIT, missing capital inputs, invalid tax inputs without an allowed proxy, zero/non-positive invested capital, and division errors yield unavailable observations with structured reasons.
- Financial arithmetic uses BigDecimal with documented scale and rounding.

## Automated Test Strategy

### Backend unit tests

- Derived ROIC calculation with average invested capital.
- Tax-rate normalization, conservative proxy use, and invalid-rate handling.
- Provider precedence at the five-observation threshold.
- Derived fallback below the provider threshold.
- Mixed provenance where permitted, with no invisible source substitution.
- Wide, narrow, none, and insufficient moat outcomes from selected observations.
- Missing-input and non-positive-denominator paths.

### Backend persistence and integration tests

- PostgreSQL/H2 migration compatibility and entity/repository mapping.
- FMP and Yahoo adapter mapping for available annual input fields.
- Seed/ingestion replay does not create contradictory duplicates.
- Moat endpoint and security review packet serialize per-year provenance and methodology.
- Unknown symbols and authorization retain existing behavior.
- Default regression fixtures cover the `INGR` provider-limited shape without requiring credentials or network access.

### Frontend tests

- API types accept provider, derived, and unavailable observations.
- The review moat section renders source and formula details for each year.
- Unavailable years show a reason rather than an empty chart point or fabricated zero.
- The derived-methodology and decision-support disclaimer is visible.
- Existing ROIC/WACC chart and summary remain usable with mixed or partial series.

## Manual Review

1. Re-ingest a configured `INGR` dataset or use the deterministic regression fixture.
2. Open `GET /api/v1/securities/INGR/moat` and the `INGR` review packet.
3. Confirm at least five eligible annual observations are present when inputs support them.
4. Recalculate a sample derived year from the displayed inputs/method and compare it with the persisted ROIC within the documented rounding tolerance.
5. Confirm every point names its source and that derived points include formula/method notes.
6. Open the review page and verify provenance and the disclaimer are visible and understandable.
7. Inspect a deliberately incomplete fixture and confirm `INSUFFICIENT_DATA` plus specific unavailable reasons.

Live FMP validation is optional and must use the gitignored `application-fmpkey.yml` profile. No secret or raw provider payload may enter test output, fixtures, source control, or screenshots.

## Suggested Commands

- `cd backend && ./mvnw -Dtest='*Moat*,*Seed*,*Ingestion*,*Review*' test`
- `cd backend && ./mvnw test`
- `cd frontend && npm test -- --run`
- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- `git diff --check`

Commands may be adjusted to the scripts actually present in the repository; missing tooling or unavailable live credentials must be recorded rather than bypassed.

## Merge Criteria

- All functional acceptance checks are implemented or explicitly documented as blocked with evidence.
- PostgreSQL and H2 migrations are forward-only and pass relevant tests.
- Provider, derived-fallback, and true-insufficient-data paths have automated coverage.
- API and UI expose per-observation provenance and the approved disclaimer.
- No external provider call occurs during ordinary moat/review reads.
- Existing backend and frontend checks pass, with any unrelated pre-existing failure identified.
- `git diff --check` passes.
- The diff contains no credentials, raw provider payloads, or unrelated feature work.

## Key Risks

- Accounting definitions vary by provider and issuer; explicit formula versions and provenance are required to keep results auditable.
- Restatements and incomplete historical balance sheets can create inconsistent denominators; immutable snapshots and structured unavailable reasons must be preserved.
- A single-period invested-capital denominator is less comparable than an average denominator and must never be presented without qualification.
- API expansion can break strict frontend fixtures; changes should remain additive where practical and be covered end to end.
