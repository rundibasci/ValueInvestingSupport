# TA2 — Implementation Plan

## 1. Vertex-Compatible `responseSchema` Derivation

1. Read `vis-model-training/schemas/thesis-output.schema.json` end to end and enumerate every construct Vertex's `responseSchema` subset does not support: the `evidence` `$ref`/`$defs` (referenced by `bullCase`/`bearCase`), and any nullable-field pattern (`type: [...]`) inherited by the broader thesis schema family.
2. Add `vis-model-training/src/vis_training/vertex/__init__.py` and `vis_training/vertex/schema_adapter.py` with a pure function `to_vertex_response_schema(source_schema: dict) -> dict` that: inlines the `evidence` `$ref` directly into `bullCase`/`bearCase`'s `items`; converts any `type: [X, "null"]` pattern to `{"type": X, "nullable": true}` (plain `nullable: true` alongside a single `type`, not combined with `anyOf` — confirmed unsupported by Vertex); drops `$schema`/`$id`/`additionalProperties` keys Vertex's subset does not recognize; preserves every `enum`, `required`, `minLength`/`maxLength`/`minimum`/`maximum`, and `minItems`/`uniqueItems` constraint Vertex's subset does support unchanged.
3. Add `vis_training/vertex/schema_adapter.py`'s companion equivalence check: a function `assert_schema_equivalent(source_schema: dict, vertex_schema: dict) -> None` (or a pytest assertion helper) verifying every property name, `required` entry, and `enum` value set present in the source schema has a corresponding entry in the derived Vertex schema — so a future edit to `thesis-output.schema.json` that isn't mirrored in the derivation logic fails a test, not a live API call.
4. Add `vis-model-training/tests/vertex/test_vertex_schema_adapter.py`: loads the real `schemas/thesis-output.schema.json`, runs `to_vertex_response_schema`, asserts the equivalence check passes, and asserts specific known-problematic constructs are actually transformed (the `evidence` `$ref` is inlined with no `$ref` key remaining anywhere in the output; no `type` array remains anywhere in the output).

## 2. `config/vertex-gemini-v1.json`

1. Model the file on the existing `config/teacher-v3.json` convention (`formatVersion`, nested config objects, path references) — write `vis-model-training/config/vertex-gemini-v1.json` with:
   ```json
   {
     "formatVersion": "1.0",
     "model": {
       "provider": "Vertex AI",
       "modelId": "gemini-2.5-flash",
       "location": "europe-west1"
     },
     "generationConfig": {
       "temperature": 0.0,
       "maxOutputTokens": <value>,
       "responseMimeType": "application/json",
       "responseSchema": <derived schema from Group 1>
     },
     "groundingTools": [],
     "promptPath": "prompts/system-prompt-v2.txt",
     "promptVersion": "system-prompt-v2",
     "outputSchemaPath": "schemas/thesis-output.schema.json",
     "inputSchemaPath": "schemas/thesis-input.schema.json",
     "validatorEntryPoint": "TRAIN-02 CLI (unchanged)",
     "fewShotPolicy": {
       "enabled": false,
       "rationale": "keeps parity with TRAIN-03's validated zero-shot design"
     }
   }
   ```
   (exact key names/nesting to match `teacher-v3.json`'s established style once both files are compared side by side — the shape above is the content contract, not necessarily the final key layout).
2. Set `maxOutputTokens` from the existing TRAIN-03 benchmark's recorded observed output length (`vis-model-training/reports/baseline/gemma-3-4b-it-v1/`), not an arbitrary new number — read that report's output-length data before choosing the value.
3. Add a small script or test asserting `config/vertex-gemini-v1.json` parses as valid JSON and its `generationConfig.responseSchema` is byte-for-byte the output of `to_vertex_response_schema(load("schemas/thesis-output.schema.json"))` from Group 1 — so the checked-in config file cannot silently drift from the derivation logic that produced it.

## 3. Prompt Adaptation Review

1. Re-read `prompts/system-prompt-v2.txt` in full against the fact that `responseMimeType`/`responseSchema` now enforce structure at the API level.
2. Write a short review note (in this spec's `requirements.md` decisions section, already drafted, or as a comment/companion note alongside the prompt) recording the conclusion: keep unchanged, or specify exactly what a new variant would need to say and why — do not silently create a variant without this recorded rationale.
3. If a variant is concluded necessary: add `prompts/system-prompt-v3.txt` and update `config/vertex-gemini-v1.json`'s `promptVersion` accordingly, with the diff from v2 documented in the same review note. (Default expectation per this phase's requirements.md: no variant is created unless this review finds a real conflict — treat this as the exception path, not the default step.)

## 4. Decoding and Few-Shot Documentation

1. Add the non-bit-exact-determinism caveat (verified: Google's own documentation states `temperature: 0.0` is the deterministic-equivalent minimum but does not guarantee identical output across repeated calls) as an explicit note in `config/vertex-gemini-v1.json`'s companion documentation (this spec's `requirements.md`/`validation.md`, and optionally a short comment field if the config format supports one) — TA3's benchmark design must read and account for this note.
2. Record the few-shot exclusion decision (10 TRAIN-01 examples remain reference-only, not injected) directly in `config/vertex-gemini-v1.json`'s `fewShotPolicy` block from Group 2, with the one-line rationale already drafted in `requirements.md`.

## 5. Reconciliation and Closure

1. Confirm `git diff --stat` against `main` shows only: this spec's 3 files, `vis-model-training/src/vis_training/vertex/*`, `vis-model-training/tests/vertex/*`, `vis-model-training/config/vertex-gemini-v1.json`, and (only if Group 3 concludes it's needed) a new `prompts/system-prompt-v3.txt`. No change to `thesis-input.schema.json`, `thesis-output.schema.json`, `prompts/system-prompt-v2.txt` (unless Group 3's exception path applies to a *new* v3 file, never an in-place edit of v2), any `backend/`, `frontend/`, or `terraform/` path.
2. Run `cd vis-model-training && .venv/bin/pytest tests/vertex/` (and the existing full suite, to confirm no regression) — see `validation.md` for exact commands.
3. Update `specs/roadmap.md` → Phase TA2 to `*(complete)*`, matching the existing convention, only after validation passes.
