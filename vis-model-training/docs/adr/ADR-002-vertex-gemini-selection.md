# ADR-002 — Vertex AI Gemini Selection

- Status: Accepted
- Decision date: 2026-08-27
- Scope: TA1 (`specs/roadmap.md` → Group TA)

## Context

The VIS Investment Thesis Agent turns financial context already calculated by deterministic VIS engines (DCF, Graham Number, DDM, Margin of Safety, Value Score, Moat Assessment) into a grounded, structured investment thesis: bull case, bear case, key risks, key assumptions, invalidation conditions. It must not retrieve external facts, recalculate valuation metrics, or issue `BUY`/`SELL`/`HOLD` instructions — the same decision-support boundary that governs every other VIS output.

`vis-model-training/`'s local programme pursued this with a self-hosted, fine-tuned model: `google/gemma-3-27b-it` as a teacher generating candidate training examples, distilled via QLoRA into a `google/gemma-3-4b-it` adapter (ADR-001). That programme closed `NO_GO` on 2026-08-24 (`vis-model-training/reports/teacher/train-05-failure-and-qlora-pause.md`) after failing its strict-output capability gate three times in a row. The failure mode was structural, not a prompting gap: the self-hosted checkpoint could not reliably produce schema-conforming JSON — it wrapped output in Markdown fences even under corrected prompting — and scored far below threshold on grounding, risk coverage, and decision-support safety. No amount of further prompt iteration against the same checkpoint was expected to close that gap.

A managed model with native controlled generation (JSON-schema-constrained decoding enforced by the serving API itself, not by the model's own compliance with an instruction) removes that exact failure mode without requiring any GPU training infrastructure, adapter, or fine-tuning run.

## Decision

Google Cloud Vertex AI's managed Gemini API replaces the local Gemma teacher/QLoRA adapter path as the production inference engine for the Investment Thesis Agent. This is an engine substitution under an unchanged task contract, not a redesign of the agent's scope, input/output shape, or safety boundary.

### Reuse Inventory

**Carried over unchanged** — none of these encode anything specific to the retired local-model engine; all operate on the task contract, not the model that fills it:

| Item | Why it carries over |
|---|---|
| `schemas/thesis-input.schema.json`, `schemas/thesis-output.schema.json` | Define the task contract (what goes in, what must come out) — independent of which model is asked to fill it. |
| `prompts/system-prompt-v2.txt` | The instruction baseline; Vertex AI's controlled generation is an additive constraint on top of it, not a replacement for it. |
| TRAIN-02 validator CLI | Validates any model's output against the schema/semantic rules (evidence grounding, `INSUFFICIENT_DATA` handling, prohibited-recommendation ban) — model-agnostic by design. |
| TRAIN-03 50-case benchmark harness/dataset | A fixed evaluation set; reusable against any candidate engine for direct comparison. |
| TRAIN-04 500-scenario generator/dataset | Synthetic `VIS*`-prefixed scenarios designed to prevent knowledge leakage from any model's pretraining — applies equally to Gemini. |
| TRAIN-12 runtime-integration contract design | Request/response envelope, error codes, deterministic-fallback rule, versioning, responsibility matrix, audit policy — all engine-agnostic; only the model-identity fields inside it change (Phase TA4). |

**Retired** — every item below exists only to support a self-hosted, fine-tuned adapter, which no longer exists in this path:

| Item | Why it's retired |
|---|---|
| TRAIN-05 (teacher/critic generation pipeline) | No teacher is needed; Gemini is the production engine directly, not a training-data generator for a smaller student. |
| TRAIN-06 (fine-tuning dataset curation) | No fine-tuning occurs. |
| TRAIN-07 / TRAIN-08 (QLoRA environment, pilot training) | No adapter is trained. |
| TRAIN-09 / TRAIN-10 (adapter comparative evaluation, data/training iteration) | No adapter exists to evaluate or iterate on. |
| TRAIN-11 (adapter packaging/release) | No adapter artifact to package or distribute. |

The closed local Gemma/QLoRA path itself (ADR-001, `train-05-failure-and-qlora-pause.md`) remains paused under its own documented re-entry criteria. This ADR does not reopen, reverse, or further judge that closure — it records a separate, forward decision to use a different engine.

## Rationale

- The TRAIN-05 failure was structural (unreliable schema conformance from a self-hosted checkpoint), and Vertex AI Gemini's controlled generation (`responseMimeType: application/json` + `responseSchema`) enforces structural JSON conformance at the serving layer, directly targeting that failure mode rather than asking a smaller model to comply better through prompting alone.
- No GPU training infrastructure, adapter artifact, or fine-tuning spend is required — inference cost becomes pay-per-call instead of GPU-hours per training run, which is a materially simpler operational and cost model for a decision-support feature with variable, on-demand usage (Phase TA4/TA5).
- The task contract, validator, and safety boundary are unchanged, so the capability gate that closed TRAIN-05 can be reapplied unchanged (Phase TA3) — this is an evidence-based substitution, not an assumption that a bigger managed model is automatically better.
- `specs/mission.md` and `specs/tech-stack.md` already name Vertex AI Gemini specifically as the intended engine for this capability (Principle 15, Cloud Distribution Path note, Vertex AI table) — this ADR formalizes a decision the project's own architecture documents already anticipated, rather than introducing a new direction.

## Alternatives Considered

- **Continue the local Gemma/QLoRA path with further prompt-engineering iteration.** Rejected: the closure report characterizes the failure as structural (the checkpoint's inability to reliably emit schema-conforming JSON, not a wording problem in the prompt), so further iteration against the same checkpoint was not expected to close the gap. Three consecutive gate failures under corrected prompting is the evidence for this, not an assumption.
- **A different managed LLM provider.** Out of scope for this ADR: `specs/mission.md` (Principle 15) and `specs/tech-stack.md` (Vertex AI table) already name Vertex AI Gemini specifically as the project's intended engine for AI-assisted thesis synthesis. Revisiting the provider choice itself would require amending those documents first, which this ADR does not do.
- **A larger self-hosted open-weight model (e.g. a bigger Gemma tier) instead of a managed API.** Rejected: this would still require GPU training/serving infrastructure and would not obviously fix the structural JSON-conformance failure, since that failure was about reliable instruction-following under load, not raw model scale alone; a managed API with API-level controlled generation addresses the specific failure mode more directly.

## Consequences

- **No further local fine-tuning, adapter training, or GPU spend is authorized under this decision.** The closed Gemma/QLoRA path remains paused under its own re-entry criteria (`train-05-failure-and-qlora-pause.md`); nothing in this ADR reopens it.
- TA2 reuses the schemas and system prompt unchanged, adapting only the request shape (controlled generation, decoding config) needed to call Vertex AI.
- TA3 must rerun the same capability gate that closed TRAIN-05, on the same TRAIN-03/TRAIN-04 datasets, plus a new real-ticker knowledge-leakage set specific to the risk that a large general-purpose model overrides supplied evidence with real-world knowledge about a named company — a risk the smaller, domain-scoped Gemma checkpoint's synthetic-only evaluation never had to clear.
- TA4 introduces the VIS backend `InvestmentThesisClient` and the `investment_thesis_result` persistence, adapting TRAIN-12's runtime contract by repointing model-identity fields at a pinned `GEMINI_MODEL_ID` instead of an adapter checksum.
- Vertex AI authentication uses a service-account key handled the same way as every other credential in this project (Secret Manager in deployed environments, gitignored local key file, never a static API key in source) — no new secrets-handling exception is introduced.

### Cost Governance

- **TA3 benchmark budget estimate:** TRAIN-03 (50 cases) + TRAIN-04 (500 scenarios) + TA3's new real-ticker knowledge-leakage set (20–30 cases) ≈ **570–580 total Gemini calls** for the one-time capability-gate run. This is an order-of-magnitude estimate; exact per-call cost depends on TA2's Flash-vs-Pro model-tier decision, which is not made by this ADR. If TA2 selects Pro-tier over an assumed Flash-tier baseline, this estimate should be revisited before TA3 spends against it.
- **Production-time governance (TA4/TA5):** `THESIS_GENERATION_DAILY_LIMIT` (named in `specs/tech-stack.md`'s environment-variable table) applies per-user to every authenticated role that can request thesis generation. No separate ADMIN-only bulk/batch generation path is authorized by this decision — mirroring the existing quota/cost-governance pattern already established for named seed packs (`specs/roadmap.md` → Group H8) rather than inventing a new one. TA4 may add a bulk path only as an explicit, separate decision; it is not implied here.
- Region: `VERTEX_AI_LOCATION=europe-west1`, matching this project's existing GCP region for K2's infrastructure (`vis-version0`, `terraform/environments/{dev,staging}`), so the project carries one documented GCP region rather than two. See `vis-model-training/docs/governance/data-and-model-licenses.md` for the full governance-review record of this choice.

## References Reviewed

Accessed 2026-08-27 (direct fetches of Google's current Vertex AI documentation pages returned navigation-shell content rather than body text in this session's tooling; the claims below are corroborated across the independent secondary sources listed, not asserted from a single unverified page fetch):

- **Data governance — Vertex AI/Gemini does not use customer prompt/response data to train Google's models without explicit permission.** Consistent with Google's published AI/ML Privacy Commitment for Google Cloud. Sources: Google Cloud's Vertex AI generative AI data-governance documentation (`cloud.google.com/vertex-ai/generative-ai/docs/data-governance`); Google Workspace's generative AI security/privacy page (<https://workspace.google.com/security/ai-privacy/>).
- **Controlled generation / structured output** — the Gemini API on Vertex AI supports `responseMimeType: application/json` combined with a `responseSchema` object to constrain output to a defined JSON shape, confirmed via Google's controlled-generation code samples (`docs.cloud.google.com/vertex-ai/generative-ai/docs/samples/generativeaionvertexai-gemini-controlled-generation-response-schema-2` and the adjoining `response-mime-type` sample).
- **Region availability — `europe-west1` (Belgium) is a supported Vertex AI region for running Gemini, including EU data-residency pinning.** **Caveat carried forward to TA2:** availability of specific Gemini model versions/tiers in `europe-west1` is version-dependent, not universal across every Gemini release — TA2 must confirm the *specific* pinned `GEMINI_MODEL_ID` it selects is actually available in `europe-west1` before finalizing that model choice, rather than assuming every Gemini tier is available in every region. Source: Google's data-residency documentation for generative AI (`docs.cloud.google.com/vertex-ai/generative-ai/docs/learn/data-residency`) and the general Vertex AI locations reference (`cloud.google.com/vertex-ai/docs/general/locations`).
- Vertex AI service account / Application Default Credentials authentication (unchanged mechanism, applies generally to Vertex AI APIs): <https://cloud.google.com/docs/authentication/provide-credentials-adc>
