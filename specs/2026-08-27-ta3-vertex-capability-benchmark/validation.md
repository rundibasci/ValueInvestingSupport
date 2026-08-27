# TA3 (preparatory pass) — Validation

## Scope Note

This pass covers only the zero-cost/zero-live-call preparatory portion of TA3 (explicit user decision, 2026-08-27). **`specs/roadmap.md` → Phase TA3 stays unmarked** — the acceptance checks below validate the infrastructure this pass built, not a completed capability-benchmark gate decision.

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
