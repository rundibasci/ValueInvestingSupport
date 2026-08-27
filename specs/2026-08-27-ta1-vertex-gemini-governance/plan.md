# TA1 — Implementation Plan

## 1. Reuse Inventory and ADR-002

1. Re-read `vis-model-training/reports/teacher/train-05-failure-and-qlora-pause.md` and confirm the exact closure reason (schema-conformance failure, Markdown-fence wrapping, grounding/risk-coverage/safety scores below threshold) is cited accurately in the new ADR — do not paraphrase the failure mode loosely.
2. Build the reuse-inventory table from `specs/roadmap.md` → Group TA's own summary paragraph: carried-over items (`thesis-input.schema.json`, `thesis-output.schema.json`, `system-prompt-v2.txt`, TRAIN-02 validator CLI, TRAIN-03 harness/dataset, TRAIN-04 generator/dataset, TRAIN-12 runtime-contract design) vs. retired items (TRAIN-05, TRAIN-06, TRAIN-07/08, TRAIN-09/10, TRAIN-11), each with one line stating why it does or doesn't apply to a managed-API engine.
3. Write `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md` using `ADR-001-model-selection.md`'s exact section order: Status/Decision date/Scope header, Context, Decision, Rationale, Alternatives Considered, Consequences, References Reviewed.
   - Context: cite the TRAIN-05 closure and its structural failure mode.
   - Decision: Vertex AI Gemini (managed API, no self-hosted weights/adapter) replaces the local Gemma/QLoRA path as the production inference engine for the Investment Thesis Agent; the reuse-inventory table from step 2 is included or linked.
   - Alternatives Considered: at minimum (a) continuing the local Gemma/QLoRA path with further prompt-engineering iteration — rejected because the failure mode was structural, not a prompting gap, per the closure report; (b) a different managed provider — out of scope, `specs/mission.md`/`specs/tech-stack.md` already name Vertex AI Gemini specifically.
   - Consequences: explicit statement that no further local fine-tuning/adapter/GPU spend is authorized under this decision; that the closed Gemma/QLoRA path remains paused under its own re-entry criteria, not reopened by this ADR.
   - References Reviewed: Vertex AI Gemini terms/documentation URLs actually opened during this review, with the access date — same evidentiary discipline as ADR-001's Gemma references.

## 2. Vertex AI Governance Review

1. Locate and read Google's current Vertex AI Gemini API terms of service and the specific data-handling commitment relevant here — whether prompt/response content sent to the pay-per-call Gemini API is used to train Google's models by default, and where that commitment is documented (distinguish this from any separate terms that might apply to free-tier or data-sharing-opt-in products, which are not what TA uses).
2. Record the review in `vis-model-training/docs/governance/data-and-model-licenses.md`: add a Vertex AI Gemini row/section parallel to the existing Gemma row (publisher, terms reference, access condition, status) but structured for a managed-API relationship — note that the equivalent of Gemma's "immutable revision pin" is the pinned `GEMINI_MODEL_ID` version string TA2/TA4 will configure, referenced here as a forward pointer, not decided in this phase.
3. Record the `VERTEX_AI_LOCATION=europe-west1` region decision in the same document, with the one-line rationale (matches this project's existing GCP region for K2's `vis-version0` infrastructure).
4. Create `docs/governance/data-policy.md` (new top-level file — `vis-model-training/docs/governance/` already exists, the top-level `docs/governance/` does not). Scope: what VIS-computed data may leave the platform to a third-party managed API (Vertex AI Gemini today; the pattern any future third-party API integration should follow), explicitly excluding raw user PII, credentials, and full unprocessed provider payloads, and the principles it inherits from `specs/mission.md` (data before opinion, conservative defaults, decision-support boundary, secrets never in source control).

## 3. Principles Cross-Check

1. Walk `specs/mission.md`'s numbered Design Principles (1–15) and identify which are actually implicated by a Vertex AI integration (expect: 4, 6, 7, 15 at minimum — decision-support boundary, immutable historical data, secrets handling, AI-assisted-thesis-is-interpretation-not-computation).
2. For each implicated principle, write one sentence in the requirements/ADR confirming it extends without modification to this integration — no principle is amended, relaxed, or exempted by this decision.
3. Cross-reference `specs/tech-stack.md`'s existing Vertex AI table (Provider/Model/Auth/Region/Decoding/Structured output/Grounding tools/Role/Generation trigger/Fallback/Test isolation rows) — confirm TA1's decisions are consistent with what `tech-stack.md` already states rather than introducing a second, conflicting source of truth; flag any row `tech-stack.md` already commits to that TA1 should NOT re-decide (e.g. grounding tools disabled, decoding `temperature: 0` — those are TA2 configuration, already documented, not TA1's to re-litigate).

## 4. Cost-Governance Policy Note

1. Compute the TA3 benchmark budget estimate: TRAIN-03 (50 cases) + TRAIN-04 (500 scenarios) + TA3's new real-ticker knowledge-leakage set (20–30 cases) = ~570–580 total calls for the one-time capability-gate run. Record as an explicit order-of-magnitude figure with the caveat that per-call cost depends on TA2's Flash-vs-Pro model-tier decision, not yet made.
2. Record the production-time governance decision for TA4/TA5: `THESIS_GENERATION_DAILY_LIMIT` (already named in `specs/tech-stack.md`'s environment-variable table) applies per-user to every authenticated role; no separate ADMIN-only bulk/batch generation path is authorized by this decision, mirroring H8's existing quota/cost-governance pattern (`specs/roadmap.md` → Group H8) rather than inventing a new one.
3. Write this as a standalone "Cost Governance" section inside ADR-002's Consequences (or as a short adjoining note in the same ADR file) — not a separate document, since it is a direct consequence of the engine-selection decision, not an independent governance topic.

## 5. Reconciliation and Closure

1. Update `specs/roadmap.md` → Group TA → Phase TA1 to mark `*(complete)*`, matching the existing convention used for every other closed phase in the file.
2. Confirm no code, schema, prompt, Terraform, or `.env`/Secret Manager change was introduced by this phase — `git diff --stat` should show only the new ADR, the two governance-doc updates/additions, and this spec's three files.
3. Run this phase's validation checklist (see `validation.md`) and record the outcome in `validation.md` before marking TA1 complete in the roadmap.
