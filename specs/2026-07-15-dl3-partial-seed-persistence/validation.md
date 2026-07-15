# Validation — DL3: Partial Seed Persistence Without Fabricated Valuation

## Functional Acceptance

- A ticker with valid required market data and no applicable valuation model returns `seeded_partial`.
- The partial response includes available company/profile/price/source/freshness facts and `valuation_guardrail_blocked`.
- Composite fair value, margin of safety, score, and recommendation are null for the current partial result.
- The saved security remains active and discoverable through search and company/market-data detail routes.
- Review and screener surfaces report structured unavailable/guardrail states instead of returning a generic error or zero.
- Full valuation success retains the existing complete result behavior.
- Provider `NOT_FOUND`, unusable required core data, persistence failures, and unexpected exceptions remain failures and do not create a misleading new active security.
- Retrying a partial ticker is idempotent and can upgrade to full success when later data supports valuation.
- Existing historical valuation evidence is retained but never represented as the current partial seed result.
- Both seed pages distinguish full, partial, and failed counts and explanations.

## Backend Automated Checks

- `SeedResult.partial` serializes the stable status/reason and null analytical fields.
- `SeedTickerService` catches only `ValuationNotApplicableException` inside its transaction.
- Market data written before that exception commits successfully.
- No current valuation or score is created for a guardrail-blocked partial seed.
- Independent supported risk/moat/capital-allocation analytics remain available when their own inputs suffice.
- `MarketDataException.NOT_FOUND` and unexpected runtime failures escape the ticker transaction and roll back new writes before `SeedService` maps the failed result.
- Partial retries do not duplicate annual/TTM snapshots or quotes for the same effective date.
- A later eligible retry creates a valid valuation and score without recreating the security.
- Partial source provenance includes Yahoo fallback categories and sanitized reasons where fallback occurred.
- Search returns the saved symbol and company facts.
- Security profile, financial, ratio, and price endpoints return saved platform facts.
- Review marks valuation as `GUARDRAIL_BLOCKED` and score as unavailable without synthesizing values.
- Screener behavior is deterministic for partial symbols with and without valuation-dependent filters.

Suggested commands, adjusted to actual test names:

```bash
cd backend
./mvnw -Dtest=SeedTickerServiceTest,SeedServiceTest,ValuationServiceTest test
./mvnw -Dtest=SecuritySearchControllerTest,ScreenerServiceTest,SecurityReviewServiceTest test
./mvnw -Pintegration-test -Dtest=PartialSeedPersistenceIT test
./mvnw test
./mvnw -DskipTests package
```

Use Java 21 and PostgreSQL 16 for transaction/rollback evidence. Do not accept H2-only evidence for the final persistence boundary.

## Frontend Automated Checks

- API types accept `seeded_partial`, `reasonCode`, and `reason`.
- Full, partial, and failed rows render distinct statuses.
- Mixed-batch counts reconcile exactly with the number of normalized symbols.
- Partial analytical cells render unavailable placeholders, not zero.
- Partial reason and provider/fallback coverage are visible and sanitized.
- A partial saved symbol offers appropriate research navigation.
- Direct seed and universe-curation pages use the same outcome semantics.

Suggested commands:

```bash
cd frontend
npm run typecheck
npm run build
```

Run the repository frontend test command if a runner is available at implementation time.

## PostgreSQL Transaction Scenarios

1. Seed an unseeded ticker whose provider data is valid but whose valuation models are all guardrail-blocked.
2. Verify the API returns partial success and commit completes.
3. In a new transaction, verify one active `security` plus expected fundamentals, ratios, and price rows exist.
4. Verify no current `valuation_result` or `value_score` was created for that attempt.
5. Retry with the same blocked inputs and verify row counts remain consistent with normal snapshot replacement semantics.
6. Retry with eligible valuation fixtures and verify the same security upgrades to full success.
7. Seed a provider-not-found ticker and verify no new active security or dependent snapshots remain.
8. Inject an unexpected failure after initial writes and verify the ticker transaction rolls back.

## Manual Review

1. Sign in with a role allowed to seed the shared universe.
2. Submit a small batch containing one full-success ticker, one guardrail-blocked ticker, and one genuine unavailable ticker.
3. Verify three distinct outcomes and reconciled counts.
4. Confirm the partial row shows company facts, current price when available, source coverage, partial badge, and guardrail reason.
5. Confirm fair value, MoS, score, and recommendation are visibly unavailable.
6. Open the partial symbol from search and verify saved profile/financial/ratio/price facts remain researchable.
7. Open review and verify the valuation limitation is explicit and no fabricated thesis input appears.
8. Use a valuation-dependent screener filter and verify the partial symbol is excluded or marked according to the documented filter semantics.
9. Retry the partial ticker and verify no duplicate or misleading data appears.

## Regression and Safety Checks

- Existing full-success seed responses remain compatible apart from additive nullable reason fields.
- Per-ticker failure isolation remains intact for mixed lists.
- FMP/Yahoo fallback selection and fallback observability are unchanged.
- Valuation eligibility rules and conservative assumptions are unchanged.
- Search, security detail, review, screener, pipeline, real-demo startup, and universe-curation consumers handle the additive result shape.
- No raw provider response, exception class, stack trace, secret, credential, or API key is serialized or logged newly.
- The feature does not introduce asynchronous seed execution or progress polling.
- `git diff --check` passes.

## Merge Criteria

- All functional acceptance statements pass.
- PostgreSQL proves both partial commit and genuine-failure rollback behavior.
- Focused seed/valuation/downstream tests pass on Java 21.
- Frontend typecheck and production build pass.
- Backend package passes and the broader suite has no new DL3 regression.
- Real-demo evidence confirms a partial company remains researchable without fabricated analytical values.
- Documentation clearly separates platform facts, analytical unavailability, interpretation, and uncertainty.

