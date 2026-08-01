# ADR-001 — Gemma Student and Teacher Selection

- Status: Accepted
- Decision date: 2026-08-01
- Scope: TRAIN-00

## Context

The VIS Investment Thesis Agent must turn financial context already calculated by deterministic VIS engines into grounded, structured JSON. It must not retrieve external facts, recalculate valuation metrics, or issue BUY, SELL, or HOLD instructions.

The initial programme needs a student small enough for a single-GPU QLoRA experiment and a materially stronger teacher for synthetic candidate generation. Model access, evaluation, training, and distribution must remain compatible with one documented governance boundary.

## Decision

The initial student is:

```text
google/gemma-3-4b-it
```

The initial teacher candidate is:

```text
google/gemma-3-27b-it
```

Both identifiers refer to instruction-tuned Gemma 3 repositories published by Google. Before the first inference, benchmark, or training run, the responsible script or run manifest must resolve and record an immutable model revision/commit. A moving branch name such as `main` is not a reproducible revision and is not accepted in a release manifest.

The 27B selection authorizes evaluation in TRAIN-05; it does not authorize teacher output to enter a dataset release automatically. Every candidate remains subject to schema validation, semantic validation, provenance capture, and human review.

## Rationale

- The 4B instruction-tuned model is a practical initial target for parameter-efficient fine-tuning and local iteration.
- The 27B instruction-tuned model offers a larger-capacity teacher while preserving a single Gemma-family licence analysis.
- A same-family teacher reduces licence ambiguity but may reproduce shared blind spots; deterministic validation and independent human review remain mandatory.
- Google documents Hugging Face Transformers/PEFT as supported tuning options for Gemma. Actual QLoRA compatibility and VRAM consumption remain TRAIN-07 measurements, not TRAIN-00 assumptions.

## Alternatives Considered

- **Gemma 3 12B teacher:** cheaper to run, but provides a smaller capacity gap over the 4B student. It is the preferred fallback when 27B cost or availability is unacceptable.
- **A proprietary hosted teacher:** potentially stronger, but its output-retention and model-training terms require a separate provider-specific review. No hosted teacher is authorized by this ADR.
- **Manual-only dataset:** legally simpler and useful as a fallback, but slower to scale. Manually authored cases remain required for benchmarks and quality control.
- **Teacher identical to the 4B student:** rejected as the primary plan because it offers too little capacity separation for useful distillation.

## Fallback Criteria

TRAIN-05 must stop or select a replacement teacher if the 27B candidate:

- cannot be accessed under accepted and recorded terms;
- cannot produce candidates within the approved cost and compute budget;
- fails representative schema, grounding, or prohibited-instruction tests;
- introduces unacceptable licensing or commercial-distribution constraints;
- cannot be pinned and reproduced;
- performs no better than the 12B fallback or manual baselines on the defined task.

Any replacement requires a new ADR or an explicit amendment to this ADR before candidate generation.

## Consequences

- Heavy teacher inference and CUDA-oriented QLoRA use an external NVIDIA GPU environment.
- The local Apple M4 host remains suitable for documentation, dataset tooling, deterministic validation, and bounded exploratory inference where supported.
- Model weights are gated artefacts and are never committed.
- Any adapter or distilled model is governed as a Gemma Model Derivative under the reviewed Gemma terms.
- TRAIN-03, TRAIN-05, TRAIN-07, and TRAIN-09 must link their run manifests back to this decision.

## References Reviewed

Accessed 2026-08-01:

- Gemma Terms of Use: <https://ai.google.dev/gemma/terms>
- Gemma Prohibited Use Policy: <https://ai.google.dev/gemma/prohibited_use_policy>
- Gemma fine-tuning documentation: <https://ai.google.dev/gemma/docs/tune>
- Student model card: <https://huggingface.co/google/gemma-3-4b-it>
- Teacher model card: <https://huggingface.co/google/gemma-3-27b-it>

