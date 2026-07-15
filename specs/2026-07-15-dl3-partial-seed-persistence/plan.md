# Plan — DL3: Partial Seed Persistence Without Fabricated Valuation

## Task Group 1: Partial Result Contract

1.1 Extend `SeedResult` with stable `reasonCode` and sanitized `reason` fields while retaining response compatibility for existing consumers.

1.2 Add a `partial` factory that sets status `seeded_partial`, preserves company/profile/price/source/freshness facts, leaves valuation-dependent fields null, and keeps `error=null`.

1.3 Update frontend seed types and shared status helpers to recognize full, partial, and failed outcomes explicitly.

1.4 Document the additive JSON contract and exact meaning of partial success.

## Task Group 2: Transactional Partial Persistence

2.1 Refactor `SeedTickerService.seedOne` into clear ingestion, valuation, score, and derived-analytics stages without changing its per-ticker `REQUIRES_NEW` transaction.

2.2 Catch only `ValuationNotApplicableException` at the valuation stage and return the partial result from inside the transaction.

2.3 Skip valuation-dependent score computation when no current valuation exists; continue independent derived analytics only where their contracts support missing valuation data.

2.4 Preserve `SourceTracker` cleanup in every outcome and include available category provenance in partial responses.

2.5 Verify no generic catch inside the transaction converts provider, database, mapping, or unexpected failures into commits.

## Task Group 3: Retry and Historical-State Semantics

3.1 Verify partial seed retries replace/upsert current market snapshots idempotently rather than duplicating rows.

3.2 Allow a later retry to produce full valuation and score results when sufficient data becomes available.

3.3 Ensure a partial refresh does not serialize an older valuation, MoS, score, or recommendation as the current seed result.

3.4 Keep historical valuation and research records intact with their original dates and provenance.

## Task Group 4: Downstream Availability

4.1 Exercise search, profile, financials, ratios, price, security detail, and aggregate review against a partial security.

4.2 Map missing current valuation to `GUARDRAIL_BLOCKED` with a plain-language reason when fundamentals exist but all valuation models are inapplicable.

4.3 Map missing score caused by the blocked valuation to a structured unavailable state rather than zero or a generic server error.

4.4 Verify screener queries tolerate partial securities, include them only when selected filters permit missing analytical fields, and explain exclusions when valuation/score filters require unavailable data.

4.5 Avoid adding persistent partial-state columns unless existing facts and availability responses cannot represent the state deterministically.

## Task Group 5: Seed UI Semantics

5.1 Update the direct seed page to show separate fully seeded, partially seeded, and failed counts.

5.2 Add a distinct partial badge and show the sanitized valuation guardrail reason.

5.3 Keep null fair value, MoS, score, and recommendation visibly unavailable and preserve source/fallback coverage details.

5.4 Apply the same result semantics to the universe-curation seed table and any shared helpers.

5.5 Keep links to researchable company/profile data available without implying analytical completeness.

## Task Group 6: Backend Tests

6.1 Add `SeedTickerService` tests for full success and explicit valuation-not-applicable partial success.

6.2 Prove partial success commits security, profile fields, fundamentals, ratios, quote, and available optional market data while creating no current valuation or score.

6.3 Prove provider `NOT_FOUND`, required-core-data failure, persistence exception, and unexpected exception remain failed and roll back a newly introduced security.

6.4 Add retry coverage for partial-to-partial idempotency and partial-to-full upgrade.

6.5 Verify source/fallback details and freshness survive the partial path and raw exception/provider payload details do not.

6.6 Add integration coverage for partial security search/detail/review and structured screener availability.

## Task Group 7: Frontend and Regression Validation

7.1 Validate result rendering for full, partial, mixed, and failed batches on both seed pages.

7.2 Verify null analytics never render as numeric zero, a recommendation, or a full-success count.

7.3 Run frontend typecheck/build and available page tests.

7.4 Run focused seed, valuation, search, screener, and security-review backend tests, then the broader backend suite.

7.5 Run a PostgreSQL integration scenario with a known guardrail-blocked ticker and `git diff --check` before merge.

## Task Group 8: Documentation and Demo Evidence

8.1 Update seed API and walkthrough documentation with partial-success semantics and example output.

8.2 Re-run a representative APD/WBA-style seed without inventing values and record whether the symbol remains researchable.

8.3 State separately which facts were persisted, which analytics were blocked, why they were blocked, and which provider/fallback supplied each category.

