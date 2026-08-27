# Data and Model Licence Register

- Review date: 2026-08-01 (Gemma models); **2026-08-27 addendum: Vertex AI Gemini, TA1**
- Scope: TRAIN-00 engineering compliance inventory; extended by TA1 (`specs/roadmap.md` → Group TA) for the managed-API engine that superseded the local Gemma/QLoRA path — see `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md`
- Legal status: not legal advice; external counsel/authorized business review is required before commercial distribution or a customer-facing hosted service

## Model Register

| Role | Model | Publisher | Terms | Access condition | Status |
|---|---|---|---|---|---|
| Student | `google/gemma-3-4b-it` | Google | Gemma Terms of Use and Prohibited Use Policy | Accept Gemma conditions at the distribution host; authenticate without committing tokens | Superseded — local QLoRA path closed `NO_GO` 2026-08-24 (`reports/teacher/train-05-failure-and-qlora-pause.md`); paused under its own re-entry criteria, not deleted |
| Teacher candidate | `google/gemma-3-27b-it` | Google | Gemma Terms of Use and Prohibited Use Policy | Same gated-access requirement | Superseded — see above; TRAIN-05 evaluation is the closed capability-gate attempt this row records |
| Teacher fallback | `google/gemma-3-12b-it` | Google | Gemma Terms of Use and Prohibited Use Policy | Same gated-access requirement | Not selected; moot following the switch to Vertex AI Gemini (ADR-002) |
| **Production inference engine (TA1+)** | **Vertex AI Gemini** (`GEMINI_MODEL_ID`, pinned version — final model tier decided in TA2/TA3) | Google Cloud (managed API, no downloadable weights) | Google Cloud Terms of Service + Vertex AI Generative AI data-governance policy (not the Gemma Terms of Use — no gated-model download, no `Model Derivative` concept applies) | Service-account authentication (Application Default Credentials) via Secret Manager (deployed) / local gitignored key file (dev) — no token/licence acceptance step comparable to Gemma's gated Hugging Face access | Approved as production engine per ADR-002; production integration gated on the TA3 capability-benchmark result (unchanged gate that closed TRAIN-05, plus a new real-ticker knowledge-leakage check) |

Immutable repository revisions must be added to the run manifest before any Gemma-family model is executed (historical/paused path only). This register intentionally does not pretend that a mutable model identifier is a revision pin — the equivalent discipline for Vertex AI Gemini is a **pinned `GEMINI_MODEL_ID` version string, never a floating/auto-updating alias** (see ADR-002's Consequences and `specs/tech-stack.md`'s Vertex AI table); Google's deprecation/EOL notice window for that pinned version must be tracked once TA2/TA4 set it.

## Vertex AI Gemini — Governance Review (TA1, 2026-08-27)

Distinct from the Gemma review above: Vertex AI Gemini is consumed as a managed, pay-per-call API relationship, not a downloaded/gated model, so the applicable governance questions are about data sent and data-use commitments, not redistribution/licence terms.

| Question | Finding | Basis |
|---|---|---|
| What data is sent to the API? | VIS-computed financial context only (already-derived valuation/score/moat fields VIS itself calculated) — never raw user PII, never authentication credentials, never unprocessed full provider payloads. Scope formalized in `docs/governance/data-policy.md`. | Design constraint carried into TA2's request-shape design; not yet implemented (no API call exists before TA2). |
| Does Google use this data to train its models by default? | No — Google Cloud's generative AI data-governance policy states customer prompt/response data on Vertex AI is not used to train or improve Google's models without explicit customer permission or instruction. | Google Cloud Vertex AI generative-AI data-governance documentation; corroborated by Google's published AI/ML Privacy Commitment for Google Cloud and Google Workspace's generative-AI security/privacy documentation. See ADR-002 → References Reviewed for the exact sources checked. |
| Region / data residency | `VERTEX_AI_LOCATION=europe-west1`, matching this project's existing GCP region for K2's infrastructure (`vis-version0`). Confirmed as a supported Vertex AI region for Gemini, including EU data-residency pinning. **Caveat:** specific Gemini model-tier availability in `europe-west1` is version-dependent — TA2 must confirm its chosen `GEMINI_MODEL_ID` is actually available in this region before finalizing the model selection. | Google's Vertex AI generative-AI data-residency documentation and general Vertex AI locations reference. See ADR-002 → References Reviewed. |
| Redistribution / `Model Derivative` concept (as applies to Gemma) | Not applicable — Vertex AI Gemini has no downloadable weights and produces no adapter/derivative artifact; nothing here is "distributed" in the sense the Gemma Terms of Use define. | Structural difference between a downloaded gated model and a managed inference API. |
| Auth / secret handling | Service-account key via Application Default Credentials, injected through Secret Manager (deployed environments) or a local gitignored key file (`GOOGLE_APPLICATION_CREDENTIALS`, dev) — same handling class already used for the FMP key and the K2 Google OAuth client secret. No exception to `specs/mission.md` Principle 7 (secrets never in source control) is introduced. | `specs/tech-stack.md` → Vertex AI table; consistent with the existing `.env` / `application-fmpkey.yml` gitignore pattern. |

Nothing in this review authorizes sending real user PII, authentication material, or unprocessed provider payloads to Vertex AI — only VIS-derived financial context, exactly as `docs/governance/data-policy.md` scopes it.

## Terms Assessment

The Gemma Terms of Use reviewed on 2026-08-01 report a last-modified date of 2026-04-01. They:

- permit use, reproduction, modification, and distribution subject to the agreement;
- state that Google claims no rights in generated outputs;
- define models trained through transfer of Gemma output patterns, including synthetic-output distillation, as `Model Derivatives`;
- require distribution of Gemma or Model Derivatives to carry the agreement, applicable use restrictions, modification notices, and the prescribed Gemma notice for non-hosted distribution;
- incorporate the Gemma Prohibited Use Policy and applicable-law obligations;
- place responsibility for generated outputs and subsequent uses on the user.

Consequently, using Gemma 3 27B outputs to train the Gemma 3 4B adapter is permitted for this engineering plan only subject to those terms. The resulting adapter/model is treated as a Model Derivative. This is not an unconditional commercial clearance.

## Required Distribution Notice

Before TRAIN-11 distributes an adapter or other Model Derivative outside a hosted service, packaging must include the exact notice required by the then-current Gemma Terms of Use and a copy of the applicable agreement. The notice must be copied from the official terms during release preparation rather than relying on this register as a frozen legal template.

Modified files must carry appropriate modification notices. Hosted-service and commercial deployment obligations must be re-reviewed against the terms effective on the release date.

## Dataset Register

| Dataset or source | Origin | Rights basis | Permitted use | Redistribution status |
|---|---|---|---|---|
| `examples/example-001.json` through `example-003.json` | Manually authored synthetic VIS cases | Project-authored; no real issuer facts or third-party prose | Validation, benchmark preparation, and training once approved | May be stored in this repository |
| `datasets/seed-dataset-v1.jsonl` | Exact JSONL aggregation of the three manual cases | Same as source examples | Validation and future training preparation | May be stored in this repository |
| Future deterministic scenarios | Generated from project-authored rules | Project-authored synthetic data | Candidate generation and dataset construction | Release only after provenance and validation |
| Future Gemma teacher outputs | Generated by a revision-pinned Gemma teacher | Gemma output governed by the reviewed terms; derivative workflow applies | Candidate pool only until validated and human-reviewed | Do not publish as a release dataset until TRAIN-06 approval |
| VIS/provider financial data | Not authorized by TRAIN-00 | Provider/data licence must be assessed per source | None by default | Prohibited until separately approved |

Every added source requires its owner, source/version, acquisition or generation date, licence/terms reference, transformation, permitted uses, redistribution limits, and reviewer to be recorded before release.

## Teacher Output Conditions

Teacher candidates must record:

- teacher model identifier and immutable revision;
- tokenizer and prompt version;
- inference configuration;
- generation timestamp and run identifier;
- synthetic scenario identifier and generator version;
- raw-candidate retention classification;
- automatic validation results;
- human reviewer and review status.

Teacher output never enters a training release directly. It first enters a restricted candidate area, then passes validation, deduplication, provenance review, and explicit human approval.

## Commercial and Regulatory Boundary

- This inventory does not determine whether a particular deployment constitutes regulated financial advice.
- VIS outputs remain decision support and require the MiFID II disclaimer when later shown in the application — this applies identically to AI-generated thesis text (Vertex AI Gemini) as to every other Fair Value/Value Score output (`specs/mission.md` Principle 15).
- No model may issue operational BUY, SELL, or HOLD instructions — applies to Vertex AI Gemini exactly as it applied to the (now-superseded) Gemma path.
- Legal review is mandatory before commercial adapter distribution, customer-facing hosted inference, or use of provider-derived financial data. For Vertex AI Gemini specifically: legal review of the Google Cloud Terms of Service and data-processing terms applicable at the actual release date is mandatory before any commercial/customer-facing release — this register's TA1 review is an engineering compliance check, not that legal review.

## Official References

### Gemma (accessed 2026-08-01 — historical/paused path)

- Gemma Terms of Use: <https://ai.google.dev/gemma/terms>
- Gemma Prohibited Use Policy: <https://ai.google.dev/gemma/prohibited_use_policy>
- Gemma intended-use statement: <https://ai.google.dev/gemma/intended_use_statement>
- Gemma fine-tuning documentation: <https://ai.google.dev/gemma/docs/tune>
- Gemma 3 4B IT model card: <https://huggingface.co/google/gemma-3-4b-it>
- Gemma 3 27B IT model card: <https://huggingface.co/google/gemma-3-27b-it>

### Vertex AI Gemini (accessed 2026-08-27 — TA1, current production engine)

See `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md` → References Reviewed for the full source list and access notes (data governance, controlled generation, region availability, authentication).

