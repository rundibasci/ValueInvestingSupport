# TA1 — Validation

## Acceptance Checks

- [ ] `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md` exists with the same section structure as `ADR-001-model-selection.md` (Status/Decision date/Scope, Context, Decision, Rationale, Alternatives Considered, Consequences, References Reviewed).
- [ ] ADR-002's Context section accurately cites the TRAIN-05 closure reason from `vis-model-training/reports/teacher/train-05-failure-and-qlora-pause.md` (structural schema-conformance failure, not a prompting gap).
- [ ] ADR-002 contains an explicit reuse-inventory table: carried-over items (schemas, prompt baseline, TRAIN-02/03/04/12) vs. retired items (TRAIN-05/06/07/08/09/10/11), each with a one-line reason.
- [ ] ADR-002's Consequences explicitly state no further local fine-tuning/adapter/GPU spend is authorized, and that the closed Gemma/QLoRA path remains paused under its own re-entry criteria (not reopened).
- [ ] ADR-002 records `VERTEX_AI_LOCATION=europe-west1` and the rationale (matches K2's existing GCP region).
- [ ] ADR-002 contains a Cost Governance section/subsection with the ~570–580-call TA3 budget estimate (with the Flash/Pro cost caveat) and the `THESIS_GENERATION_DAILY_LIMIT`-applies-to-every-role / no-implied-ADMIN-bulk-path decision.
- [ ] `vis-model-training/docs/governance/data-and-model-licenses.md` has a new Vertex AI Gemini entry, parallel in structure to the existing Gemma entry, reflecting a managed-API relationship (no revision-checksum pin; forward-pointer to the `GEMINI_MODEL_ID` pin TA2/TA4 will set).
- [ ] `docs/governance/data-policy.md` exists (new top-level file) and scopes what VIS data may leave the platform to a third-party API, excluding raw PII/credentials/full provider payloads, inheriting explicitly-named `specs/mission.md` principles.
- [ ] The ADR or requirements.md explicitly confirms each implicated `specs/mission.md` principle (at minimum 4, 6, 7, 15) extends without exception — no principle amended or relaxed.
- [ ] `specs/roadmap.md` → Phase TA1 is marked `*(complete)*`.

## Validation Commands

- `git diff --stat main` — confirms the diff touches only: this spec's 3 files, `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md`, `vis-model-training/docs/governance/data-and-model-licenses.md`, `docs/governance/data-policy.md`, and `specs/roadmap.md`. No `backend/`, `frontend/`, `terraform/`, `.env*`, or `vis-model-training/schemas|prompts|scripts` change.
- `grep -rn "GOOGLE_APPLICATION_CREDENTIALS\|GEMINI_MODEL_ID\|VERTEX_AI_LOCATION" vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md` — confirms the location decision and the forward-pointer to model-pinning are both present as text, not silently omitted.
- No `mvn test`, `npm run build`, or `terraform validate` applies — this phase introduces no code.

## Manual Review

- Read ADR-002 end to end and confirm it would let a reader unfamiliar with `vis-model-training/`'s history understand *why* the engine changed, *what* stays the same (task contract, validator, safety boundary), and *what* is explicitly not authorized (further local training spend) — matching the bar ADR-001 already sets for the original Gemma selection.
- Confirm the cost-governance estimate is presented as an order-of-magnitude figure with its stated dependency on TA2's model-tier decision, not as a committed final number that TA3 could be held to unfairly.
- Confirm `docs/governance/data-policy.md` stays narrowly scoped to third-party data egress and does not duplicate or contradict `specs/mission.md`'s existing Design Principles.

## Merge Readiness

- All acceptance checks above are satisfied.
- Worktree contains only TA1's governance documents, this spec directory, and the roadmap status update — no code, schema, prompt, or infrastructure change.
- No Vertex AI API call was made and no GCP resource was created during this phase.

## Known Risks

- This is an engineering compliance review, not a legal opinion — external counsel/authorized business review remains required before any commercial, customer-facing use of Vertex AI-derived output, mirroring the exact caveat already carried by `data-and-model-licenses.md`'s Gemma review.
- The TA3 cost estimate is provisional until TA2 fixes the Flash-vs-Pro model tier; if TA2's choice diverges materially from a Flash-tier cost assumption, this estimate should be revisited before TA3 spends against it rather than treated as final.
