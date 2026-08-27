# TA1 — Governance, Model Selection & Reuse Assessment

## Context

`vis-model-training/`'s local `google/gemma-3-27b-it` teacher pipeline and `google/gemma-3-4b-it` QLoRA adapter path closed `NO_GO` on 2026-08-24 (`vis-model-training/reports/teacher/train-05-failure-and-qlora-pause.md`) after failing its strict-output capability gate three times in a row: the self-hosted checkpoint could not reliably produce schema-conforming JSON (it wrapped output in Markdown fences even under corrected prompting) and scored far below threshold on grounding, risk coverage, and decision-support safety.

Group TA (`specs/roadmap.md`) replaces the local-model engine with Google Cloud Vertex AI's managed Gemini API for the same task — turning VIS-computed financial context into a structured investment thesis (bull case, bear case, risks, invalidation conditions) — without changing the task contract, the validator, or the decision-support boundary. TA1 is the governance gate before any Vertex AI call is made or any spend occurs: it records the switch decision, confirms the new managed-API vendor relationship is compatible with this project's existing governance principles, and sets a cost-governance policy before TA3's benchmark run (the first phase that actually calls the live API) is authorized.

TA1 produces documentation and decisions only. It does not call Vertex AI, does not write application code, and does not create any GCP resource. It is independent of Group K (`specs/mission.md` → Cloud Distribution Path) — Vertex AI is a managed API reachable via a service account the same way FMP is reachable via an API key, with no dependency on K1–K3.

## Scope

### ADR: switch from local Gemma/QLoRA to Vertex AI Gemini

- Record `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md`, matching the section structure and tone of `ADR-001-model-selection.md` (Status/Decision date/Scope, Context, Decision, Rationale, Alternatives Considered, Consequences, References Reviewed).
- Explicit reuse inventory, split into carried-over vs. retired, matching the roadmap's own summary:
  - **Carried over unchanged:** task contract (`schemas/thesis-input.schema.json`, `schemas/thesis-output.schema.json`), system prompt baseline (`prompts/system-prompt-v2.txt`), the TRAIN-02 validator CLI, the TRAIN-03 50-case benchmark harness/dataset, the TRAIN-04 500-scenario generator/dataset, the TRAIN-12 runtime-integration contract design.
  - **Retired:** TRAIN-05 (teacher/critic generation), TRAIN-06 (fine-tuning dataset curation), TRAIN-07/TRAIN-08 (QLoRA environment and pilot training), TRAIN-09/TRAIN-10 (adapter evaluation and iteration), TRAIN-11 (adapter packaging/release) — none apply to a managed-API engine with no adapter artifact.
  - Explicit statement that the local Gemma/QLoRA path stays paused under its own documented re-entry criteria (`train-05-failure-and-qlora-pause.md`); this ADR does not reopen, reverse, or further judge that closure.
- Explicit statement that no further local fine-tuning, adapter training, or GPU spend is authorized under this decision (mirrors the Group TA acceptance checklist).

### Governance review: Vertex AI as a managed third-party API

- New review, distinct from TRAIN-00's open-weight/Gemma review (`specs/2026-08-01-train-00-decisions-prerequisites/`) — Vertex AI is a hosted API relationship, not a downloaded model with redistribution terms.
- Confirm and record, as of the review date:
  - Vertex AI's terms of service for the specific data being sent: VIS-derived financial context (computed valuation/score/moat fields already stored by VIS), never raw user PII, never authentication credentials, never full provider payloads.
  - Confirmation that the Vertex AI Gemini API (the non-training, pay-per-call inference endpoint used here) does not use customer prompt/response data to train Google's models by default, and where that confirmation is documented by Google.
  - Region/data-residency decision for `VERTEX_AI_LOCATION`: **`europe-west1`**, matching the region already selected for this project's GCP infrastructure (`vis-version0`, K2 `dev`/`staging` — see `terraform/environments/*/variables.tf`), so this project has one documented GCP region instead of two.
- Update `vis-model-training/docs/governance/data-and-model-licenses.md` with a new register row/section for Vertex AI Gemini, parallel in structure to the existing Gemma row (publisher, terms reference, access condition, status), but reflecting a managed-API relationship rather than a downloaded/gated model — no revision-pinning-by-checksum concept applies here; the equivalent is the pinned `GEMINI_MODEL_ID` version string (TA2/TA4 concern, referenced here as a forward pointer).
- Create `docs/governance/data-policy.md` — referenced by the roadmap's TA1 bullet but not yet present anywhere in the repository (`vis-model-training/docs/governance/` exists; the top-level `docs/governance/` does not). Scope it as the project-wide data-handling policy this application-level (not `vis-model-training/`-scoped) governance decision belongs under: what VIS data may leave the platform to a third-party API, under what conditions, and which principles it inherits from `specs/mission.md`.

### Confirm existing principles extend cleanly

- Walk `specs/mission.md`'s Design Principles against a managed-API integration and record, for each principle actually implicated, that no exception is being requested:
  - Principle 4 (decision-support boundary, MiFID II disclaimer) — already the explicit framing of Group TA and Principle 15.
  - Principle 7 (secrets never in source control) — Vertex AI auth is a service-account key, same handling class as the Google OAuth client secret already in Secret Manager (K2) and the FMP key pattern; no static API key.
  - Principle 6 (immutable historical data) — anticipates TA4's `investment_thesis_result` append-only design (not created in TA1, but the principle is confirmed compatible here).
- No principle in `specs/mission.md` requires an amendment or exception for this integration; record that conclusion explicitly rather than leaving it implicit.

### Cost-governance policy

- Budget estimate for the TA3 benchmark run: the existing TRAIN-03 (50 cases) + TRAIN-04 (500 scenarios) = 550 scored calls, plus TA3's new real-ticker knowledge-leakage set (20–30 cases) = **roughly 570–580 total Gemini calls** for the one-time TA3 benchmark pass. Record this as an order-of-magnitude estimate (exact pricing depends on the TA2 model-tier decision between Flash and Pro), not a committed final figure — TA3 itself records actual spend against this estimate per the roadmap's acceptance checklist.
- Explicit decision on production-time governance for on-demand generation (TA4/TA5), mirroring H8's existing quota/cost-governance pattern (admin-only controls where cost/quota requires it, `specs/roadmap.md` → Group H8): a per-user daily cap (`THESIS_GENERATION_DAILY_LIMIT`, already named in `specs/tech-stack.md`'s environment-variable table) applies to every authenticated role; no separate ADMIN-only bulk/batch generation path is authorized by this decision — TA4 may only add one if it is later an explicit, separate decision, not implied by TA1.
- This policy note is a decision record, not a running cost-tracking mechanism; actual metering/alerting on Vertex AI spend is a TA4/K3 operational concern, not a TA1 deliverable.

## Decisions

1. **Vertex AI Gemini replaces the local Gemma/QLoRA path as the production inference engine**, recorded in ADR-002. This is an engine substitution under an unchanged task contract and validator, not a redesign of the Investment Thesis Agent's scope or safety boundary.
2. **No further local fine-tuning, adapter training, or GPU spend is authorized.** The closed Gemma/QLoRA path (`train-05-failure-and-qlora-pause.md`) remains paused under its own re-entry criteria; ADR-002 does not reopen it.
3. **`VERTEX_AI_LOCATION` = `europe-west1`**, matching this project's existing GCP region for K2, recorded here and carried forward unchanged into TA2/TA4's actual client configuration.
4. **The Gemini API relationship is governed as a managed third-party API, not a downloaded/gated model** — the applicable governance questions are data-sent, training-use-of-customer-data, and region, not licence/redistribution terms as with Gemma.
5. **Cost governance is decided before any TA3 spend**: the ~570–580-call TA3 benchmark budget estimate and the `THESIS_GENERATION_DAILY_LIMIT` per-user production cap are both recorded in this phase, not deferred to TA3/TA4.
6. **All implicated `specs/mission.md` principles extend without exception** to this integration; this is recorded explicitly, not assumed.

## Out of Scope

- Calling the Vertex AI API in any form (test, smoke, or otherwise) — TA2 is the first phase that constructs a real request shape; TA3 is the first phase that calls the live API.
- Selecting the specific Gemini model tier (Flash vs. Pro) — that is TA2's initial candidate decision, finalized by TA3's capability-gate result.
- Any change to the prompt contract, schemas, or validator — all carried over unchanged per this phase's own reuse inventory; TA2 owns any adaptation.
- Any application code, Terraform resource, Secret Manager entry, or `.gitignore` rule for `GOOGLE_APPLICATION_CREDENTIALS` — TA4 owns the VIS backend client and its secret-handling; TA1 only confirms the handling class is already compatible with existing principles.
- A definitive legal opinion on GDPR/data-residency/commercial obligations — this phase records an engineering compliance review, the same explicit caveat TRAIN-00's licence register already carries ("not legal advice; external counsel/authorized business review is required before commercial distribution").
- Final production rate-limit *value* tuning or enforcement — TA4 implements `THESIS_GENERATION_DAILY_LIMIT` enforcement; TA1 only records the governance decision that a per-user cap applies and that no ADMIN-bulk path is implied.

## Compatibility and Risks

- Vertex AI's terms of service and data-use commitments can change; this review must record the exact date reviewed, the same discipline `ADR-001`/`data-and-model-licenses.md` already apply to the Gemma terms, so a later phase can detect drift rather than relying on a stale assumption.
- Recording `VERTEX_AI_LOCATION=europe-west1` here is a decision, not yet a running configuration — TA2/TA4 must carry the exact same value into `application.yml`/Terraform when they actually configure the client; a silent mismatch here would defeat the point of deciding it in TA1.
- The cost-governance budget estimate is only as good as the TA2 model-tier decision it precedes; if TA2 selects Pro-tier over Flash-tier (materially different per-call cost), TA1's estimate should be revisited before TA3 actually spends against it — flagged here so TA2/TA3 do not silently treat this as a fixed number.
- `docs/governance/data-policy.md` is a new top-level governance document with no existing precedent in this repository (only `vis-model-training/docs/governance/` exists today); its scope must stay narrowly about what leaves the platform to third-party APIs, not become a general catch-all, to avoid duplicating `specs/mission.md`'s existing design principles.
