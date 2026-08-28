# TA3 — Validation

## Scope Note

This document now covers two passes: the zero-cost/zero-live-call **preparatory pass** (2026-08-27, unchanged below) and the **live benchmark run** (2026-08-28, new section below) that actually called Vertex AI Gemini for all 574 cases. **`specs/roadmap.md` → Phase TA3 still stays unmarked** — human review of the prepared samples (`results/vertex-gemini-2.5-flash-v1/review/*.review.json`) and the resulting go/no-go gate decision against `config/capability-probe-gate.json` are still outstanding; see "Live Run" → "What Remains" below.

## Preparatory Pass (2026-08-27)

## Acceptance Checks

- [ ] `vis_training/benchmark/vertex_backend.py` exists, implements `GenerationBackend`, loads all request-shape configuration from `config/vertex-gemini-v1.json`, resolves `GOOGLE_CLOUD_PROJECT`/credentials from the environment only, and raises `VertexBackendConfigurationError` on missing config or non-empty `groundingTools`.
- [ ] `tests/vertex/test_vertex_backend.py` covers config loading, request construction, both credential-resolution paths, and both configuration-rejection cases — every test uses an injected fake client, zero network calls.
- [ ] `datasets/benchmark/real-ticker-knowledge-leakage-v1.jsonl` contains exactly 24 records (6 companies × 4 variants), each schema-valid against both `thesis-input.schema.json` and `thesis-output.schema.json`, each with a unique `exampleId` and `metadata.benchmarkCategory = "REAL_TICKER_KNOWLEDGE_LEAKAGE"`.
- [ ] `vis_training/vertex/real_ticker_dataset.py` generates that file reproducibly; `tests/vertex/test_real_ticker_dataset.py` asserts the checked-in file matches the generator's live output byte-for-byte.
- [ ] `rubrics/manual-review-v1.json` documents the new `knowledgeLeakage` dimension as conditional (`appliesTo: REAL_TICKER_KNOWLEDGE_LEAKAGE cases only`), not universal.
- [ ] `vis_training/benchmark/review.py`'s `prepare_review_form`/`validate_completed_review` require `knowledgeLeakage` only for that category; `tests/benchmark/test_knowledge_leakage_review.py` proves both the positive and negative case, and that the pre-existing `minimum_category_count=9` default behavior is unchanged.
- [ ] `config/capability-probe-gate.json` has `minimumRealTickerKnowledgeLeakageCaseCount` (20) and `minimumKnowledgeLeakageAcceptRate` (0.80) added, every pre-existing field unchanged.
- [ ] `google-genai==1.47.0` is declared in `pyproject.toml` and present in the regenerated `requirements.lock`.

## Validation Commands

- `cd vis-model-training && .venv/bin/pytest` — full suite passes with zero regressions (124 passed at the time this phase was written: 88 pre-TA baseline + 11 TA2 schema-adapter/config tests + 11 TA3 `VertexBackend` tests + 9 TA3 real-ticker-dataset tests + 5 TA3 knowledge-leakage-review tests).
- `cd vis-model-training && PYTHONPATH=src .venv/bin/python3 scripts/build_real_ticker_dataset.py --output datasets/benchmark/real-ticker-knowledge-leakage-v1.jsonl` — regenerates the dataset deterministically; `git diff` against the checked-in copy must be empty.
- `cd vis-model-training && PYTHONPATH=src .venv/bin/python3 scripts/validate_dataset.py --dataset datasets/benchmark/real-ticker-knowledge-leakage-v1.jsonl --input-schema schemas/thesis-input.schema.json --output-schema schemas/thesis-output.schema.json --format json` — `"valid": 24, "invalid": 0, "warnings": 0, "errors": 0"` (verified this session).
- `python3 -c "import json; json.load(open('vis-model-training/config/capability-probe-gate.json'))"` — valid JSON.
- `git diff --stat main` — confirms the diff matches exactly the file list in `plan.md` → Group 4, step 2. No `backend/`, `frontend/`, `terraform/`, `.env*`, `thesis-input.schema.json`, `thesis-output.schema.json`, `prompts/system-prompt-v2.txt`, or `config/vertex-gemini-v1.json` change.
- `grep -rn "GOOGLE_APPLICATION_CREDENTIALS\|GOOGLE_CLOUD_PROJECT" vis-model-training/config/vertex-gemini-v1.json` — returns nothing (credentials never written to this config file, confirmed both by this grep and by `VertexBackend`'s own design).

## Manual Review

- Spot-check 3–4 generated real-ticker records: does `expected` actually follow only the supplied (fake) evidence, with no trace of the real company's real-world reputation? (Verified for AAPL/MSFT/KO samples this session — see plan.md Group 2.)
- Confirm each company's `realFact` metadata is itself accurate and easily independently verifiable (all six are extremely well-known, uncontroversial facts: FCF/earnings-growth reputations and Dividend King status are public, long-standing, widely cited facts, not close calls).
- Confirm `VertexBackend`'s `manifest()` output really contains no credential material (asserted by test, also worth a human glance given this is exactly the kind of thing that's easy to leak by accident in a `manifest()`/logging method).

## Merge Readiness

- All acceptance checks above are satisfied.
- Full `vis-model-training` test suite green, zero regressions.
- No live Vertex AI call was made, no GCP resource was created or modified, no credential was written anywhere during this pass.
- `specs/roadmap.md` → Phase TA3 is explicitly left unmarked, with this validation document recording exactly what remains before it can be marked complete (see Known Risks and `requirements.md` → Out of Scope).

## Known Risks

- **Untested against the real Vertex AI API.** Every test here mocks the client; the first live call (TA3's actual benchmark run) is the only thing that can confirm Vertex AI accepts the derived `responseSchema`, the exact model id `gemini-2.5-flash` in `europe-west1`, and the `maxOutputTokens` estimate at request time.
- **TRAIN-05-era critic-specific gate fields are unresolved for this pipeline shape** (`minimumUsableCriticRate`, `minimumCanonicalCriticRate`, `minimumDecisiveCriticRate`, `expectedCandidateSlots` in `config/capability-probe-gate.json`) — TA3's live run must explicitly decide whether these apply, are reinterpreted, or are retired before the gate can actually be evaluated.
- **The real-ticker dataset's `expected` field is template-derived, not individually hand-authored per case** — reasonable for exercising the harness mechanically, but a human reviewer should sanity-check a broader sample (not just the 3 spot-checked here) before treating its classification/confidence values as a fully authoritative gold answer, particularly for the payout-ratio and overvaluation variants.
- **This pass does not touch Google Cloud project setup.** `vis-version0` (this project's existing K2 GCP project, per the user's decision this session) needs the Vertex AI API enabled and a runtime credential path decided before the live run can start — deferred to that future session.

## Live Run (2026-08-28)

Explicit user authorization this session ("procediamo con TA3" / "procedi con il batch completo"). ADC already present for `marcellomazzoni@gmail.com` on `vis-version0` (residue of K1/K2 GCP work); Vertex AI API enabled by the user via console mid-session after a first 403 `SERVICE_DISABLED` call (zero cost — rejected before generation).

### Two real request-shape defects found and fixed via live smoke tests (all zero/near-zero cost — rejected client-side or server-side before generation, except the final successful smoke call)

1. **`uniqueItems` unsupported by Vertex's `responseSchema`.** `google-genai`'s client-side `types.Schema` has no `uniqueItems` field; rejected with a pydantic `ValidationError` before any network call. Fixed in `vis_training/vertex/schema_adapter.py`: moved from `_PASSTHROUGH_KEYWORDS` to `_UNSUPPORTED_KEYWORDS` (dropped, like `additionalProperties`/`$schema`/`$id`).
2. **Bare `enum` nodes need an explicit `type`.** Second live call reached the network and got `400 INVALID_ARGUMENT: "response schemas didn't specify the schema type field"`. Fixed in the same adapter: infers `type: "string"` for any string-valued enum lacking an explicit type.
3. **`gemini-2.5-flash`'s default "thinking" silently ate the output budget.** Third call (first real generation, real spend) succeeded but returned truncated JSON — `finish_reason=MAX_TOKENS`, `thoughts_token_count=767` vs. `candidates_token_count=242` against `maxOutputTokens=1024`. Fixed by adding `thinkingBudget: 0` to `config/vertex-gemini-v1.json`'s `generationConfig` and wiring `VertexBackend` to pass `types.ThinkingConfig(thinking_budget=...)` when present (`None` when absent = API default, not forced off). Fourth call: complete, valid, schema-compliant JSON.

All three fixes are covered by new/updated tests (`tests/vertex/test_vertex_backend.py`, `tests/vertex/test_vertex_schema_adapter.py`); `config/vertex-gemini-v1.json`'s `responseSchema` was regenerated live from the fixed adapter (not hand-edited) so the TA2 byte-identical drift test stays meaningful.

### TRAIN-04's scenarios-v1.jsonl was not runnable as-is

`datasets/candidates/scenarios-v1.jsonl` (TRAIN-04's raw generator output — `scenarioId`/`scenarioType`/`input`, no `messages`/`metadata` envelope) does not match `BenchmarkRunner`'s 3-message contract; it was built for TRAIN-05's closed `TeacherBackend`/critic pipeline, a different interface than `VertexBackend`'s `GenerationBackend`. Built a converter instead of forking the harness:

- `vis_training/vertex/expected_thesis.py` (new): the grounded-only `expected`-derivation logic, extracted out of `real_ticker_dataset.py` so both datasets share one implementation.
- `vis_training/vertex/scenarios_benchmark_dataset.py` + `scripts/build_scenarios_benchmark_dataset.py` (new): converts each scenario into the harness's 3-message contract, `expected` derived by `derive_expected_thesis` (input field set confirmed identical to `real_ticker_dataset.py`'s baseline). Output: `datasets/benchmark/scenarios-benchmark-v1.jsonl` (500 records, checked in, byte-identical-to-generator test).

### `derive_expected_thesis` had a real classification bug, found and fixed via live comparison (not a Gemini error)

Original logic only reached `POTENTIALLY_OVERVALUED` through a red-flag branch (declining trend / payout > 100%); a negative margin with *no* separate red flag silently fell through to `FAIRLY_VALUED`. Confirmed wrong empirically: all four real-ticker `fabricated-overvaluation` cases (margin −55%, valueScore 22) got this wrong template answer while Gemini correctly said `POTENTIALLY_OVERVALUED` in all four. Fixed by making margin sign/magnitude the primary classification driver (reusing the roadmap's own existing MoS convention — Z5's UI gauge: >15% green, 5–15% yellow, <5%/negative red — for classification, not just color), with a single red flag only downgrading a *thin* (5–15%) margin to `UNDER_REVIEW`, not a comfortable (>15%) one. `derive_expected_thesis` also now handles `dataQuality=INSUFFICIENT` (forces `INSUFFICIENT_DATA`), `INSUFFICIENT`/`INCONSISTENT`/`STALE`/`CONTRADICTORY_SIGNALS` (forces `humanReviewRequired=true`), and never cites a null input field as evidence — none of which the real-ticker-only design had ever exercised. New tests: `tests/vertex/test_scenarios_benchmark_dataset.py` (runs TRAIN-02's actual `validate_semantics` against every derived record, not just schema shape).

Effect on the classification-match metric (a template-vs-model comparison, not a correctness oracle — see below): real-ticker 0.75 → 1.0; TRAIN-04 scenarios 0.598 → 0.666 (bug fix) → 0.814 (MoS-threshold refinement). Refinement was deliberately stopped once remaining mismatches (93/500, largest single pattern 41) stopped clustering into one fixable rule and started looking like genuine model-vs-heuristic judgment differences — continuing would mean overfitting this independent reference to one model's specific style rather than keeping it an independent check.

### Full batch results

`scripts/run_vertex_benchmark.py` (new, idempotent/resumable via `BenchmarkRunner`'s existing exampleId-skip): all 574 cases (50 TRAIN-03 + 500 TRAIN-04-converted + 24 real-ticker) generated successfully — **0 generation errors, 0 JSON parse errors, 100% schema compliance** across every case. Estimated spend: low single-digit dollars (Flash tier, ~574 calls, ~980 avg input / ~450 avg output tokens). Raw results: `results/vertex-gemini-2.5-flash-v1/*.results.jsonl`; metrics: `results/vertex-gemini-2.5-flash-v1/metrics/*.metrics.json`.

Direct comparison to the closed Gemma baseline (`reports/baseline/gemma-3-4b-it-v1/metrics.json`, `global`): Gemma scored `jsonValidityRate: 0.0` and `classificationAccuracy: 0.0` across all 50 cases (never produced valid JSON at all). Gemini: `jsonValidityRate`/`schemaComplianceRate` = 1.0 across all 574.

### TRAIN-02's semantic validator run against Gemini's *actual output* (not the template) — zero genuine violations

`validate_semantics` (the same function `scripts/validate_dataset.py` uses) was run against every one of the 574 real `parsedOutput` records. 12/574 flagged; all 12 manually verified as false positives of the mechanical check, not real defects:
- 5 `PROHIBITED_RECOMMENDATION` (TRAIN-04 `ADVERSARIAL_INPUT` cases): the model is correctly *warning that an injected "buy recommendation" in `deterministicWarnings` is untrusted and should be disregarded* — the regex can't distinguish citing a prohibited phrase from using it.
- 3 `UNSUPPORTED_NUMERIC_CLAIM`: mostly the schema-required `confidence` field being caught by the numeric-claim scanner; one borderline case (a stated "2.0" leverage threshold in `invalidationConditions`) worth a human glance but not alarming.
- 4 `EVIDENCE_FIELD_NULL` (13 occurrences, `INSUFFICIENT`/`PARTIAL`-quality scenarios): the model cites a null field specifically to say the data is *missing* ("the absence of an intrinsic value calculation makes it impossible to assess..."), not to fabricate a value — correct decision-support behavior; the validator rule doesn't yet distinguish "citing a value" from "citing an absence."

### Finding requiring human-review attention: `humanReviewRequired` does not respond to fundamental deterioration alone

Statistically clean pattern, not noise: across every TRAIN-04 scenario with a `STRONGLY_DECLINING` trend that is *not* already flagged via `dataQuality`/`CONTRADICTORY_SIGNALS` (68 cases — `VALUE_TRAP` 32, `FCF_DETERIORATION` 21, `DIVIDEND_RISK` 15), **`humanReviewRequired` was `false` in 68/68 cases (100%)** — regardless of how severe the decline. On `base-benchmark-v1`'s hand-authored TRAIN-03 gold answers, the same pattern holds on `VALUE_TRAP`/`DIVIDEND_AT_RISK`/`ADVERSARIAL` (gold: `UNDER_REVIEW`/`humanReviewRequired=true`; Gemini: `POTENTIALLY_UNDERVALUED`/`humanReviewRequired=false`, even while its own `bearCase` correctly lists the same declining-fundamentals evidence). By contrast, `STALE_DATA` and `CONTRADICTIONS` cases *do* correctly get `humanReviewRequired=true`. So the flag responds reliably to explicit data-quality problems but not to fundamental business deterioration by itself — exactly the "cheap stock, deteriorating fundamentals" (value trap) pattern this platform's mission exists to catch. This is real Gemini behavior on the real system prompt, not a template or harness defect — not something this pass fixes (`prompts/system-prompt-v2.txt` is explicitly out of scope for TA3). Flagged here as the single most important item for the human reviewer's `riskQuality`/`inputAdherence` scoring, and as a candidate follow-up for TA4 prompt tuning if the human review confirms it as a real gap rather than an acceptable design tradeoff.

### Human Review — Complete (2026-08-28)

All 68 prepared cases reviewed by `marcellomazzoni` (`results/vertex-gemini-2.5-flash-v1/review/{real-ticker,base-benchmark,scenarios}.review.json`, `check-review` valid on all three at their actual category counts). Combined accept rate 55/68 (0.8088). Real-ticker `knowledgeLeakage`: 20/20 scored 2 (no leakage). 13 cases marked `accepted: false`, all attributable to the single `humanReviewRequired`/value-trap pattern below, not scattered defects.

### Gate Evaluation — Partial

Mechanically computable `capability-probe-gate.json` fields all pass (`minimumParseableRate`, `minimumStructuralValidRate`, `minimumHumanReviewCount`, `minimumHumanCategoryCount`, `minimumHumanAcceptRate` at 0.8088 — a thin margin over the 0.80 floor, `minimumRealTickerKnowledgeLeakageCaseCount`, `minimumKnowledgeLeakageAcceptRate` at 1.0, `maximumValidatorFalsePositiveRate` at 0.021). Two field groups still **not evaluated** — decision remains open, not silently resolved:
1. `minimumAverageScores` (`grounding`/`classification`/`riskCoverage`/`decisionSupportSafety`) — names don't map 1:1 to this rubric's five dimensions.
2. `minimumUsableCriticRate`/`minimumCanonicalCriticRate`/`minimumDecisiveCriticRate`/`expectedCandidateSlots` — TRAIN-05 critic-pipeline concepts with no equivalent in TA3's design.

### Comparison Report — Complete

`reports/vertex/gemini-2.5-flash-v1/` (README, environment, run-manifest, metrics, manual-review, error-analysis, cost, checksums) — full comparison against the closed Gemma baseline. Headline: Gemma scored 0% JSON validity / 0% classification accuracy / 15% human-review accept rate on its 20-case review; Gemini scored 100% / 52–100% (dataset-dependent, see report) / 80.9% on 68 cases. The report's `error-analysis.md` documents the `humanReviewRequired`/value-trap finding as the leading TA4 prompt-tuning candidate, not a structural defect.

### Prompt-Tuning Experiment (2026-08-28) — Candidate, Not Adopted

Explored whether the `humanReviewRequired`/value-trap gap is fixable in the prompt: it traces to a specific, narrow hole in `prompts/system-prompt-v2.txt` Rule 9 (enumerates STALE/INCONSISTENT/INSUFFICIENT/CONTRADICTORY_SIGNALS but never a `STRONGLY_DECLINING` trend). A one-clause addition (`STRONGLY_DECLINING` trend + positive margin → `humanReviewRequired=true`) was tested live against a separate variant prompt file (`prompts/system-prompt-v2-ta3-experiment-hr.txt`, never wired into production config) on the 13 non-accepted cases plus 6 controls: **100% reliability (6/6) where the clause's exact condition held, zero false positives on controls.** Projected effect on the accept-rate margin: 0.8088 → ~0.897. **Production `prompts/system-prompt-v2.txt` is unchanged** — explicit user decision to document this as a TA4 candidate rather than adopt it within TA3's scope. Full writeup: `reports/vertex/gemini-2.5-flash-v1/experiments/human-review-rule-experiment.md`.

### What Remains

- **The two unresolved gate-field groups above** — an explicit decision (apply/reinterpret/retire) is still needed before the gate can be called fully evaluated.
- **Whether to adopt the prompt-tuning candidate above** — would require a full 574-case re-run, re-review, and re-gate before it could actually move the accept-rate margin for real (see the experiment doc's "What would be needed to actually adopt this").
- **The actual go/no-go decision itself** — the evidence base is now complete (this document + the comparison report + the prompt-tuning experiment), but the decision has not been made.
- `specs/roadmap.md` → Phase TA3 stays unmarked until the go/no-go decision is made — explicit user instruction this session (2026-08-28): report written, phase not yet closed.
