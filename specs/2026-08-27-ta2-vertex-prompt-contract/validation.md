# TA2 — Validation

## Acceptance Checks

- [ ] `vis-model-training/src/vis_training/vertex/schema_adapter.py` exists with `to_vertex_response_schema()` and an equivalence-check function; no `$ref`, `$defs`, or `type` array remains anywhere in its output.
- [ ] `vis-model-training/tests/vertex/test_vertex_schema_adapter.py` loads the real `schemas/thesis-output.schema.json`, runs the adapter, and asserts equivalence plus the specific `$ref`-inlining and nullable-conversion behavior.
- [ ] `vis-model-training/config/vertex-gemini-v1.json` exists, is valid JSON, and its `generationConfig.responseSchema` is verified (by test, not manual inspection) to equal the live output of the schema adapter run against the real `thesis-output.schema.json` — no drift between the checked-in config and the derivation logic.
- [ ] `config/vertex-gemini-v1.json` pins `model.modelId = "gemini-2.5-flash"`, `model.location = "europe-west1"`, `generationConfig.temperature = 0.0`, `generationConfig.responseMimeType = "application/json"`, and an explicit empty/absent grounding-tools configuration.
- [ ] `maxOutputTokens` is derived from and documented against the TRAIN-03 baseline's recorded average output length (`reports/baseline/gemma-3-4b-it-v1/error-analysis.md`), not an arbitrary value, with its safety-margin rationale recorded in the config or this spec.
- [ ] The non-bit-exact-determinism caveat and the few-shot-exclusion decision are both recorded in `config/vertex-gemini-v1.json` and/or this spec, not left implicit.
- [ ] The prompt-adaptation review conclusion (keep `system-prompt-v2.txt` unchanged, or the exact reasoning for a new `system-prompt-v3.txt`) is recorded explicitly.
- [ ] `thesis-input.schema.json`, `thesis-output.schema.json`, and `prompts/system-prompt-v2.txt` are unmodified (byte-identical to `main`) unless the prompt-review exception path is taken, in which case only a new v3 file is added, never an in-place edit.
- [ ] `specs/roadmap.md` → Phase TA2 is marked `*(complete)*`.

## Validation Commands

- `cd vis-model-training && .venv/bin/pytest tests/vertex/ -v` — new schema-adapter and config-equivalence tests pass.
- `cd vis-model-training && .venv/bin/pytest` — full existing suite (TRAIN-02/03/04/05 tests) still passes with zero regressions; TA2 must not have touched anything those tests depend on.
- `python3 -c "import json; json.load(open('vis-model-training/config/vertex-gemini-v1.json'))"` — confirms valid JSON independent of the pytest suite.
- `git diff --stat main` — confirms the diff is limited to: this spec's 3 files, `vis-model-training/src/vis_training/vertex/*`, `vis-model-training/tests/vertex/*`, `vis-model-training/config/vertex-gemini-v1.json`, `specs/roadmap.md`, and (only if the prompt-review exception applies) a new `prompts/system-prompt-v3.txt`. No `backend/`, `frontend/`, `terraform/`, `.env*`, or in-place edit to any TA1-carried-over reuse artifact.
- No live network call, Google Cloud credential, or `GOOGLE_APPLICATION_CREDENTIALS` reference appears anywhere in this phase's diff — grep confirms: `grep -rn "GOOGLE_APPLICATION_CREDENTIALS\|aiplatform\." vis-model-training/src/vis_training/vertex/` returns nothing (no actual Vertex AI SDK/API client code — that is TA4's scope).

## Manual Review

- Read `config/vertex-gemini-v1.json` end to end and confirm a reader unfamiliar with this phase could reconstruct exactly what request Vertex AI will receive in TA3/TA4, without needing to cross-reference the schema-adapter source code.
- Confirm the derived `responseSchema` inside the config, read on its own, is a plausible, complete OpenAPI-3.0-subset schema — no dangling reference, no unsupported construct a Vertex AI request would reject outright.
- Confirm the prompt-adaptation review note gives a real reason for its conclusion (whether "unchanged" or "new variant"), not a placeholder statement.

## Merge Readiness

- All acceptance checks above are satisfied.
- Worktree contains only TA2's config/adapter/test artifacts, this spec directory, and the roadmap status update.
- No Vertex AI API call was made and no GCP resource was created or touched during this phase (unchanged from TA1's discipline).

## Known Risks

- The equivalence check only verifies structural correspondence (properties, required fields, enums) — it cannot independently verify Vertex will actually accept the derived schema at request time, since Vertex's exact validation behavior is only observable by a live call. TA3's first live benchmark call is the actual confirmation; if TA3 discovers a rejected schema construct this check didn't catch, the adapter and this equivalence check must both be extended, not just the config file patched ad hoc.
- `maxOutputTokens` is derived from Gemma's observed output length on the same task, not Gemini's — the two models are not guaranteed to produce similarly-sized JSON for the same input. TA3 should record actual observed Gemini output length and flag here if the TA2 estimate needs revision before TA4 relies on it.
