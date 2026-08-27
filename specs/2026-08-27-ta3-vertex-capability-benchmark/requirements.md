# TA3 — Capability Benchmark on Vertex AI Gemini (preparatory pass)

## Context

TA1 (governance/ADR-002) and TA2 (request-shape adaptation, `config/vertex-gemini-v1.json`) prepared everything needed to call Vertex AI Gemini under the unchanged task contract. TA3 is the phase that actually runs the capability-benchmark gate that closed TRAIN-05 — reapplied unchanged against Vertex AI Gemini, extended with a new real-ticker knowledge-leakage check — and decides go/no-go for production integration (TA4).

**This pass is preparatory only, scoped to the zero-cost/zero-live-call portion of TA3**, by explicit user decision: the actual live benchmark run (~570–580 Gemini calls, real spend) and the mandatory human review (minimum 20 cases, per `rubrics/manual-review-v1.json`, not automatable) are deferred to a later session with its own explicit authorization. **`specs/roadmap.md` → Phase TA3 is therefore NOT marked complete by this pass** — only the infrastructure this phase produces is complete and tested.

The existing TRAIN-03 benchmark harness (`vis_training/benchmark/`) only implements a `HuggingFaceBackend` (local/RunPod GPU inference) — it has no Vertex AI backend at all. This pass adds one, plus the new real-ticker dataset the roadmap's TA3 bullet requires, plus the rubric/gate extensions needed to score and gate the new `KNOWLEDGE_LEAKAGE` criterion — all of it exercised by tests, none of it calling the live API.

## Scope

### `VertexBackend` (new)

- `vis_training/benchmark/vertex_backend.py`: implements the existing `GenerationBackend` abstract interface (`generate(messages, *, max_new_tokens) -> {"text", "inputTokens", "outputTokens"}`, `manifest() -> dict`) exactly as `HuggingFaceBackend` does, so `BenchmarkRunner`/`metrics.py`/`review.py` run unchanged against Vertex AI — no fork of the harness.
- Loads model id, location, temperature, `responseMimeType`, and the derived `responseSchema` from `config/vertex-gemini-v1.json` (TA2) — never hardcodes or duplicates that configuration. Refuses to construct if `groundingTools` is non-empty (defense-in-depth: this integration reasons only over VIS-supplied context, per ADR-002/`specs/mission.md`).
- `GOOGLE_CLOUD_PROJECT` and Vertex AI credentials (Application Default Credentials) come from the environment — never read from or written to `config/vertex-gemini-v1.json`, matching every other credential in this project (`docs/governance/data-policy.md`).
- Uses the `google-genai` SDK (`vertexai=True` client mode) — added to `pyproject.toml`/`requirements.lock`, pinned (`google-genai==1.47.0`, installed and verified working against Python 3.9.6 this session).
- All tests mock the client via an injectable `client_factory` — no test in this phase makes a network call or requires real credentials, matching the test-isolation discipline `specs/tech-stack.md` already commits to for `InvestmentThesisClient`.

### Real-Ticker Knowledge-Leakage Dataset (new)

- `datasets/benchmark/real-ticker-knowledge-leakage-v1.jsonl`: 24 cases (within the roadmap's 20–30 target), built from 6 real, extremely well-known companies (AAPL, MSFT, KO, JNJ, PG, XOM) × 4 deliberate-alteration variants (fabricated FCF collapse, fabricated earnings collapse, fabricated unsustainable payout ratio, fabricated overvaluation) — every alteration uses an actual `thesis-input.schema.json` field (`thesis-input.schema.json` has no `sector` field, so the roadmap's "wrong sector" example is realized as a trend/dividend/valuation field instead).
- Generated reproducibly by `vis_training/vertex/real_ticker_dataset.py` (`scripts/build_real_ticker_dataset.py` CLI) rather than hand-authored JSON — every company entry records the real, well-established fact it relies on (for reviewer verification), and `expected` is derived generically from the supplied (deliberately wrong) input only, following the same rules `prompts/system-prompt-v2.txt` already states (never from the real-world fact) — this is what makes "did the generated thesis match `expected`, or did it leak the real fact instead" mechanically comparable via the harness's existing metrics.
- Same 3-message (`system`/`user`/`assistant`) JSONL contract as `datasets/benchmark/base-benchmark-v1.jsonl`, so the unchanged `BenchmarkRunner`/`compute_metrics`/`prepare_review_form` pipeline consumes it without modification.
- Validated against the unchanged TRAIN-02 validator CLI: 24/24 records valid, 0 errors, 0 warnings.

### Rubric and Gate Extensions

- `rubrics/manual-review-v1.json`: adds a `knowledgeLeakage` dimension (0/1/2 scale, `appliesTo: REAL_TICKER_KNOWLEDGE_LEAKAGE cases only`) — not scored on every category, since the question only makes sense for a real-company case.
- `vis_training/benchmark/review.py`: `DIMENSIONS` stays the 5 universal dimensions unchanged (backward compatible with every existing TRAIN-03 review); a new `CONDITIONAL_DIMENSIONS` map adds `knowledgeLeakage` as required only when `category == "REAL_TICKER_KNOWLEDGE_LEAKAGE"`. `validate_completed_review`'s hardcoded category-count gate (`< 9`, coupled to TRAIN-03's exact dataset) becomes a `minimum_category_count` parameter (default `9`, unchanged for any existing TRAIN-03-only review) — a review pass that also covers TRAIN-04 and/or the new real-ticker set must pass its own actual combined category count; this phase does not guess what that combined run will look like.
- `config/capability-probe-gate.json`: adds `minimumRealTickerKnowledgeLeakageCaseCount` (20) and `minimumKnowledgeLeakageAcceptRate` (0.80, matching the existing `minimumHumanAcceptRate` bar) — extending the same gate unchanged, per ADR-002/roadmap ("reapplied unchanged... extended to require a passing KNOWLEDGE_LEAKAGE rate," not a relaxation).

## Decisions

1. **This pass stops before any live Vertex AI call or human review** — explicit user scoping decision (2026-08-27). `specs/roadmap.md` → TA3 is not marked complete; only this preparatory infrastructure is.
2. **`VertexBackend` reuses the existing `GenerationBackend` interface exactly** — no new runner/metrics/review pipeline is introduced; TA3's eventual live run is `vis-benchmark run --model ... ` pointed at a `VertexBackend` instance, structurally identical to how TRAIN-03 already runs against `HuggingFaceBackend`.
3. **The real-ticker dataset's `expected` field is grounded-only by construction** (derived from supplied evidence, never real-world facts) — this is what makes automatic classification/evidence metrics meaningful for this category even though its actual purpose (catching leakage) is inherently a human-judgment call the schema/semantic validator cannot make, per the roadmap's own framing.
4. **`knowledgeLeakage` is a conditional rubric dimension, not a universal one** — scoring it on every category would ask reviewers a meaningless question ("did this synthetic `VIS*`-prefixed company leak real-world knowledge?") for the 9 existing TRAIN-03 categories and the TRAIN-04 scenarios, which by design use symbols with no real-world referent.
5. **`google-genai` (not `google-cloud-aiplatform`) is the SDK choice** — it is Google's current, actively maintained unified client for both the Gemini Developer API and Vertex AI (`vertexai=True` mode), consistent with `google-cloud-aiplatform`'s generative-model surface being deprecated in favor of it.
6. **Credentials are never read from `config/vertex-gemini-v1.json`** — `GOOGLE_CLOUD_PROJECT` and Application Default Credentials come from the environment exclusively, enforced by `VertexBackend` raising `VertexBackendConfigurationError` rather than silently defaulting.

## Out of Scope

- The actual live Vertex AI benchmark run (TRAIN-03's 50 cases + TRAIN-04's 500 scenarios + this phase's 24 real-ticker cases ≈ 574 calls, matching TA1's cost-governance estimate) — deferred, real spend, requires explicit authorization in a later session.
- The mandatory human review (minimum 20 cases across all categories, per `rubrics/manual-review-v1.json`) — not automatable, requires a human reviewer's actual judgment time.
- Applying `config/capability-probe-gate.json`'s thresholds to produce an actual go/no-go decision — there is no benchmark result to gate yet.
- The written comparison report against the closed Gemma baseline (`reports/baseline/gemma-3-4b-it-v1/`) — depends on the live run's actual results.
- Deciding whether `capability-probe-gate.json`'s TRAIN-05-era critic-specific fields (`minimumUsableCriticRate`, `minimumCanonicalCriticRate`, `minimumDecisiveCriticRate`, `expectedCandidateSlots`) still apply to a pipeline with no separate critic-review step — flagged as a risk below, not resolved here.
- Any change to `thesis-input.schema.json`, `thesis-output.schema.json`, `prompts/system-prompt-v2.txt`, or `config/vertex-gemini-v1.json`'s request shape — all carried over unchanged from TA1/TA2.
- Google Cloud project setup (enabling the Vertex AI API on `vis-version0`, service-account creation) — noted as the plan for when the live run is authorized, not performed in this pass.

## Compatibility and Risks

- **TRAIN-05-era critic-specific gate fields may not map cleanly onto a live-API-only pipeline.** TRAIN-05's gate assumed a separate Gemma "critic" model reviewing teacher candidates; TA3's design has no equivalent second-model review step (TRAIN-02's validator + human review replace that role). Whoever runs the live benchmark must explicitly decide whether `minimumUsableCriticRate`/`minimumCanonicalCriticRate`/`minimumDecisiveCriticRate`/`expectedCandidateSlots` apply, are reinterpreted, or are retired for this run — deferred, not silently resolved by this pass.
- **`VertexBackend`'s request-construction logic is untested against the real API.** The mocked tests prove the harness wiring and configuration loading are correct; they cannot prove Vertex AI actually accepts the derived `responseSchema` at request time — that is exactly what the live run's first call will confirm or refute (same caveat TA2's `validation.md` already recorded).
- **The real-ticker dataset's `expected` field is a template-generated approximation, not a human-authored gold thesis.** It is schema/semantically valid and follows the stated rules mechanically, but a human reviewer should sanity-check a sample before relying on it as the sole "did the model follow supplied evidence" signal for this category, particularly for the two payout-ratio/overvaluation variants where the classification logic is more heuristic.
- **`minimum_category_count`'s default (9) does not reflect a combined review run.** If the live run's human review pool ends up covering TRAIN-03 + TRAIN-04 + the real-ticker set together, whoever runs `check-review` must pass the actual combined category count explicitly — the code default intentionally stays 9 so it never silently under- or over-constrains an unrelated review pass.
