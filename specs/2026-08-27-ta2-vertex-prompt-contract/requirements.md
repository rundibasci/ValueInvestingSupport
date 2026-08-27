# TA2 — Vertex AI Prompt Contract & Structured-Output Adaptation

## Context

TA1 (`specs/2026-08-27-ta1-vertex-gemini-governance/`) recorded the governance decision to replace the closed local Gemma/QLoRA path with Vertex AI Gemini as the production inference engine for the Investment Thesis Agent (ADR-002), with an explicit reuse inventory: the task contract (`schemas/thesis-input.schema.json`, `schemas/thesis-output.schema.json`), the system prompt baseline (`prompts/system-prompt-v2.txt`), and the TRAIN-02 validator CLI all carry over unchanged.

TA2 adapts the *request shape* needed to call Vertex AI's Gemini API under that unchanged contract — specifically, using Vertex's controlled generation (`responseMimeType: application/json` + `responseSchema`) to enforce structural JSON conformance at the API level, directly targeting the exact failure mode that closed TRAIN-05 (the self-hosted checkpoint wrapping output in Markdown fences despite explicit prompt instructions not to). TA2 produces configuration and adaptation artifacts only — no Vertex AI API call is made in this phase (TA3 is the first phase that calls the live API, as the capability-benchmark run).

## Scope

### Vertex-Compatible `responseSchema` Derivation

- Vertex AI's `responseSchema` accepts a subset of the OpenAPI 3.0 Schema object — it does **not** support JSON Schema's `$ref`/`$defs` (every referenced definition must be inlined) and does not support a `type` array for nullable fields (it uses a separate `nullable: true` boolean alongside a single `type` instead). `thesis-output.schema.json` uses both `$ref`/`$defs` (the `evidence` definition, referenced by `bullCase`/`bearCase`) and, indirectly via `thesis-input.schema.json`, `type: ["string", "null"]`-style nullable fields elsewhere in the contract family.
- `thesis-output.schema.json` remains the unchanged, unmodified source of truth (per ADR-002's reuse decision — TA2 does not edit it). A derived, Vertex-compatible flattened schema is generated/maintained separately and embedded in `config/vertex-gemini-v1.json`.
- Add an automated equivalence check (Python, matching TRAIN-02's existing `jsonschema`/`pytest` stack) that fails if the derived Vertex schema and the source `thesis-output.schema.json` diverge — every property, required field, and enum value must match — so schema drift between the two representations is caught mechanically, not left to manual review.

### Vertex AI Request Configuration

- Deliverable: `vis-model-training/config/vertex-gemini-v1.json`, structured consistently with the existing `config/teacher-v3.json` convention (`formatVersion`, nested model/decoding config, prompt/schema path references), containing:
  - `model.modelId`: `gemini-2.5-flash` (initial candidate; TA3's capability-benchmark gate makes the final go/no-go call, with a Pro-tier model as the documented fallback candidate if Flash-tier fails the gate — this phase does not run that gate).
  - `model.location`: `europe-west1` (per TA1/ADR-002's region decision), with the documented caveat that model-tier availability in this region must be reconfirmed for the exact pinned model id before TA3's live run.
  - `generationConfig`: `temperature: 0.0` — verified against current Gemini API documentation as both the parameter's minimum accepted value and its most deterministic setting (Google's own documentation is explicit that even `temperature: 0.0` does not *guarantee* bit-exact determinism, which is why this is still a documented caveat below, not treated as a solved problem) — plus a fixed `maxOutputTokens` (derived from the existing TRAIN-03 benchmark's observed output length, not an arbitrary new value), `responseMimeType: application/json`, and the derived `responseSchema` from the section above.
  - No grounding tools configuration (Search grounding, Vertex AI Search, function calling) is present — absence, not an explicit `disabled` flag, since Vertex does not enable any grounding tool unless explicitly configured.
  - `promptPath`/`promptVersion`: pointing at `prompts/system-prompt-v2.txt` unchanged, unless the prompt-adaptation review below concludes a variant is needed.
  - `validatorPath`: pointing at the unchanged TRAIN-02 validator CLI, confirming it remains the mandatory gate applied to every Gemini response before reaching a user (unchanged in TA2; enforced at the integration layer in TA4).

### Prompt Adaptation Review

- Review `prompts/system-prompt-v2.txt` against Vertex's controlled generation: its existing instruction ("Return exactly one JSON object. Do not use Markdown fences, explanatory text, or text after the closing brace.") becomes a defense-in-depth statement rather than the sole conformance mechanism, since `responseSchema`/`responseMimeType` now enforce structure at the API level.
- Decision recorded, not assumed: keep `system-prompt-v2.txt` unchanged (no new prompt variant) unless the review identifies wording that actively conflicts with or duplicates something Vertex's structured-output configuration now handles more reliably at the API layer. A new prompt variant is only created if this review concludes one is needed — it is not a default deliverable of this phase.
- **Review outcome (recorded 2026-08-27): keep `system-prompt-v2.txt` unchanged.** Read in full against Vertex's controlled generation: the inline JSON shape example (lines 9–30) and the Markdown-fence instruction (line 5) become redundant-but-harmless defense-in-depth once `responseSchema`/`responseMimeType` enforce structure at the API level — neither actively conflicts with controlled generation, so removing them is not required and risks losing a semantic anchor a model might still use to reason about the task. Every other rule in the prompt (1–12) encodes *semantic* constraints — grounding discipline, the `INSUFFICIENT_DATA`/`humanReviewRequired` conditional-setting rules, the `STRONGLY_DECLINING` bear-case requirement, the `BUY`/`SELL`/`HOLD` ban — none of which a structural JSON schema can express or enforce. No new `system-prompt-v3.txt` is created by this phase.

### Decoding and Few-Shot Policy

- Record explicitly, in `config/vertex-gemini-v1.json` and this phase's documentation, that Vertex AI does not guarantee bit-exact determinism across identical calls the way local greedy decoding did in TRAIN-03 — a documented caveat TA3's benchmark design must account for (flagging any case whose classification or schema validity differs across repeated calls), not an assumption this phase resolves.
- Few-shot policy: the 10 manual TRAIN-01 examples remain reference/documentation examples only and are **not** injected into the Gemini prompt as few-shot content in this iteration, keeping parity with TRAIN-03's already-validated zero-shot design. Record this as an explicit, deliberate decision (with the fallback: revisit only if TA3 fails the gate on a category few-shot could plausibly fix).

## Decisions

1. **`thesis-output.schema.json` stays the unmodified source of truth.** The Vertex-compatible `responseSchema` is a derived, mechanically-verified-equivalent artifact inside `config/vertex-gemini-v1.json`, not a fork of the canonical schema.
2. **Initial model candidate: `gemini-2.5-flash`, region `europe-west1`.** Pinned as a placeholder for TA3's benchmark to confirm or override — not a final production decision (that is TA3's gate result, per ADR-002).
3. **No grounding tools are configured.** Consistent with `specs/mission.md`'s "data before opinion" principle and the existing rule against reasoning from knowledge outside the supplied input — the model reasons only over the VIS-supplied financial-context JSON.
4. **No new prompt variant unless the adaptation review requires one.** `system-prompt-v2.txt` is reused unchanged by default.
5. **Few-shot examples remain excluded from the live prompt in this iteration**, keeping parity with TRAIN-03's zero-shot design; revisiting this is an explicit TA3-gate-driven decision, not a default.
6. **TRAIN-02's validator CLI is confirmed as the unchanged mandatory gate** on every Gemini response — TA2 records this contract; TA4 wires it into the actual runtime call path.
7. **No live Vertex AI API call is made in this phase.** Every artifact TA2 produces is verifiable through the flattened-schema equivalence check and static review alone; the first live call is TA3's benchmark run.

## Out of Scope

- Any call to the live Vertex AI API, including a manual smoke test — deferred entirely to TA3, so this phase incurs zero API spend, consistent with TA1's cost-governance policy recording the TA3 benchmark as the first spend event.
- The TA3 capability-benchmark run itself and its go/no-go gate on the Flash-vs-Pro model-tier decision.
- The VIS backend `InvestmentThesisClient`, `investment_thesis_result` persistence, and the three thesis endpoints — all TA4.
- Any frontend change — TA5.
- Editing `thesis-input.schema.json`, `thesis-output.schema.json`, or `prompts/system-prompt-v2.txt` in place — carried over unchanged per ADR-002's reuse decision; TA2 only adapts a derived request shape around them.
- Actual Google Cloud project/service-account/Secret Manager setup for Vertex AI credentials — that is an operational TA4 concern (the VIS backend client's auth), not a TA2 config-artifact concern.

## Compatibility and Risks

- The flattened Vertex `responseSchema` must be regenerated (or its equivalence re-verified) if `thesis-output.schema.json` is ever changed — the automated equivalence check exists specifically so this drift is caught by a failing test, not discovered manually during a later live run.
- Vertex's exact supported `responseSchema` subset (and its exact accepted `temperature` floor for greedy-equivalent decoding) should be reconfirmed against current API documentation at the time this phase is executed — API surface details can change between TA1's governance review and TA2's implementation; do not assume TA1's high-level confirmation covers every low-level schema-dialect detail.
- Pinning `gemini-2.5-flash` now is a placeholder, not a commitment; TA3's capability gate is the actual authority on model-tier selection, and this config must be updated (not silently left stale) if TA3 selects Pro-tier instead.
- The region availability caveat carried forward from ADR-002 (Gemini model-tier availability in `europe-west1` is version-dependent) must be reconfirmed for the specific `gemini-2.5-flash` pin before TA3's live run consumes this config.
