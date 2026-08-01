# TRAIN-00 — Decisions and Prerequisites

## Context

`vis-model-training` defines an incremental SFT/QLoRA programme for a Gemma-based Investment Thesis Agent. TRAIN-01 contracts and a TRAIN-02 validator prototype already exist as exploratory work, but the legal, model-selection, hardware, and data-governance prerequisites required by TRAIN-00 have not been formalized.

This phase closes those prerequisites before dataset expansion, teacher generation, model download, benchmarking, or training. It does not alter the deterministic VIS valuation and scoring engines.

## Scope

### Model decisions

- Record `google/gemma-3-4b-it` as the initial student/base model, pinned by an immutable revision before any benchmark or training run.
- Select `google/gemma-3-27b-it` as the initial teacher model because it is a larger instruction-tuned model in the same Gemma 3 family and keeps the teacher/student legal analysis under one model licence.
- Require TRAIN-05 to benchmark the selected teacher on representative cases before accepting generated examples; selection in TRAIN-00 is authorization to evaluate, not automatic approval of its outputs.
- Define a documented fallback-selection process if the 27B teacher is unavailable, unsuitable, or uneconomic.

### Licence and governance record

- Record the applicable Gemma Terms of Use version, Gemma Prohibited Use Policy, model-card references, access conditions, distribution obligations, and commercial-use considerations.
- Record that synthetic-output distillation creates a Gemma `Model Derivative` under the current terms and therefore carries the applicable use restrictions and distribution notice obligations.
- Record the provenance and licence basis of every current and future dataset source, including manually authored synthetic examples and future teacher outputs.
- State that the repository record is an engineering compliance inventory, not legal advice; external legal review remains required before commercial distribution or a hosted customer-facing service.

### Hardware assessment

- Document the detected local environment: Apple Silicon M4, integrated 10-core GPU, macOS 15.7.7, ARM64, approximately 317 GiB free workspace volume at assessment time, no NVIDIA/CUDA toolchain detected, and system Python 3.9.6.
- Capture unified-memory capacity when the host permits it, or mark the value as not observed rather than inferring it.
- Classify the local host as suitable for repository development, validation, and possibly bounded CPU/Metal experiments, but not as the reference environment for the CUDA/bitsandbytes QLoRA plan.
- Define minimum and recommended requirements for an external single-GPU environment. Exact VRAM feasibility remains subject to TRAIN-07 smoke measurement; TRAIN-00 must not claim an unmeasured guarantee.

### Data policy and secret hygiene

- Formalize prohibited data: personal data, credentials, proprietary financial data without authorization, untraceable facts, impermissible teacher outputs, and substantial copyrighted text.
- Define required per-example provenance fields and review status.
- Run a repository secret scan using locally available tooling and inspect tracked files relevant to `vis-model-training`; record sanitized evidence only.
- Confirm that model weights, adapters, caches, raw generated candidates, and local credentials are excluded from source control where appropriate.

### Roadmap reconciliation

- Update `vis-model-training/README.md` so TRAIN-00 status and its GO/NO-GO decision match the delivered evidence.
- Mark TRAIN-01 and TRAIN-02 work completed before TRAIN-00 as exploratory, resolving the current conflict with the sequential-phase rule.
- Keep TRAIN separate from the application roadmap unless a later approved phase introduces runtime VIS integration.

## Decisions

1. **Teacher model:** use `google/gemma-3-27b-it` as the initial teacher candidate, pinned to a revision before use.
2. **Student model:** retain `google/gemma-3-4b-it` as the initial QLoRA target.
3. **External compute:** use a documented external NVIDIA GPU environment for CUDA-oriented QLoRA and heavy 27B teacher inference; do not force the repository design around the local Apple GPU.
4. **No downloads in TRAIN-00:** this phase records feasibility and requirements but does not download model weights, install ML dependencies, call hosted inference, or start training.
5. **Conditional GO:** TRAIN-00 may conclude `GO` only when the ADR, licence register, hardware report, data policy, and secret-hygiene evidence are complete. Commercial deployment remains conditional on legal review and later validation gates.
6. **Decision-support boundary:** neither teacher nor student may calculate VIS financial metrics, introduce external facts, or issue BUY/SELL/HOLD instructions.

## Out of Scope

- Adding the remaining TRAIN-01 examples.
- Completing the TRAIN-02 Python validator and test suite.
- Downloading or executing Gemma models.
- Creating benchmark, training, validation, or test datasets beyond governance metadata definitions.
- Provisioning paid GPU infrastructure or creating cloud resources.
- Running teacher generation, QLoRA, evaluation, packaging, serving, or VIS runtime integration.
- Providing a definitive legal opinion.

## Compatibility and Risks

- Gemma terms and prohibited-use policies may change; every training/release run must record the reviewed version and date.
- Hugging Face access requires acceptance of the applicable Gemma conditions; credentials or access tokens must never be committed.
- A teacher from the same model family can reinforce shared blind spots. TRAIN-05 and TRAIN-09 must retain deterministic validators and independent human review.
- The 27B teacher is materially more resource-intensive than the 4B student; external inference cost and availability must be measured before bulk generation.
- Apple Metal and CUDA/bitsandbytes behavior are not interchangeable. TRAIN-07 must establish the reproducible reference environment through an actual smoke test.
- Financial outputs remain decision support and must retain the MiFID II disclaimer wherever later surfaced to users.
