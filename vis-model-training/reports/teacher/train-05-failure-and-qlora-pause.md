# TRAIN-05 — Failure report and QLoRA pause

- Date: 2026-08-24
- Decision: `NO_GO`
- Scope: `google/gemma-3-27b-it` as teacher/critic and the downstream QLoRA programme
- Status: TRAIN-05 closed with a negative capability outcome; TRAIN-06 blocked; TRAIN-07 and TRAIN-08 paused

## Executive decision

The current Gemma-based synthetic-data and QLoRA path is discontinued for now.

- Do not run another Gemma 3 27B prompt iteration, capability probe, calibration, critic pass, or bulk generation.
- Do not promote any TRAIN-05 output into TRAIN-06.
- Do not start TRAIN-07 environment work or TRAIN-08 adapter training.
- Preserve the implementation, tests, reports, and partial probe artifact as reproducible evidence; they are not an approved training dataset.
- Reconsider QLoRA only after a separate architecture decision supported by new evidence.

This is a pause of the QLoRA programme, not a claim that parameter-efficient fine-tuning is generally ineffective. The failed dependency is the availability of a sufficiently reliable source dataset under the current teacher/critic design.

## Outcome against the objective

TRAIN-05 was intended to produce validated, independently reviewed candidates suitable for curation. It did not produce evidence that the selected engine can perform that task reliably enough for dataset production.

The pipeline itself is operational: it is resumable, preserves provenance and failures, supports deterministic validation and human review, and cleans up paid resources. Those properties do not compensate for a model that fails the required output contract and quality gates.

| Run | Teacher contract | Critic behavior | Human quality | Decision |
|---|---|---|---|---|
| Smoke v1 | 40/40 parseable and structurally valid | 15/40 canonical; all verdicts `REVIEW` | Not measured | Continue only to bounded calibration |
| Calibration v1 | 100/100 parseable and structurally valid | 36% canonical; all verdicts `REVIEW` | 11/30 accepted (36.67%) | `NO_GO` |
| Calibration v2 | 93/100 parseable; 0 structurally valid because all 93 added forbidden `candidateId` | 2/93 canonical; 1/93 decisive | 15/30 accepted (50%) | `NO_GO` |
| Capability probe v3 | 0/5 canonical JSON; five early `PARSE_REJECTED` results | Not started | Not started | Stopped early; gate mathematically impossible |

Calibration v2 human averages were 1.367 for grounding, 1.800 for classification, 1.200 for risk coverage, and 1.200 for decision-support safety. Only classification reached its threshold. Calibration v1 averages were 0.867, 1.233, 1.233, and 1.167 respectively.

## Corrective work already exhausted

Before the final probe, the implementation removed model-visible `candidateId` values from teacher and critic payloads, applied the configured decoding parameters, switched the teacher to greedy decoding, strengthened the strict-JSON and key-whitelist instructions, corrected the invalidation-direction guidance, and made decisive critic verdicts explicit. A 20-slot fake-backend dry run verified the orchestration and gate logic.

The real checkpoint still wrapped each of its first five otherwise JSON-looking responses in Markdown `json` fences. Their lengths were 579–604 tokens, so the observed failure was not caused by early truncation. The gate required 20/20 canonical teacher responses and became impossible after the first failure; stopping after five identical failures avoided further spend without weakening the decision.

Stripping Markdown fences would make those particular payloads machine-readable, but it would not demonstrate instruction adherence. More importantly, it would not address the earlier failures in grounding, risk coverage, safety, invalidation logic, unsupported qualitative judgments, human-review consistency, or independent critic decisiveness. Reclassifying recovered output as canonical success would therefore hide the failure rather than resolve it.

## Failure boundary

The evidence supports these conclusions:

1. The local pipeline and deterministic controls are functional.
2. Prompt/template and payload integration defects found in calibration v2 were corrected before the final probe.
3. `google/gemma-3-27b-it` did not meet the minimum strict-output capability gate under the corrected configuration.
4. Earlier human reviews independently show that contract recovery alone would not yield a sufficiently reliable dataset.
5. Using the same checkpoint as teacher and critic did not provide an effective independent quality gate.

The experiment does not establish whether a different model, a constrained-decoding engine, a deterministic-first architecture, or a human-authored dataset would succeed. Selecting among those options is outside this closure decision.

## Downstream impact

- **TRAIN-05:** closed as a failed capability evaluation. The originally defined positive merge gate was not met and must not be represented as met.
- **TRAIN-06:** blocked because there is no approved candidate pool to curate or release.
- **TRAIN-07:** paused; no QLoRA environment or feasibility work is authorized.
- **TRAIN-08:** paused; no pilot adapter or training expenditure is authorized.
- **TRAIN-09–12:** blocked because no approved adapter exists to evaluate, iterate, package, or hand off.

No candidate was automatically promoted to training. At closure, the RunPod pod and network-volume inventories were empty. Provider billing had not yet exposed a final charge for the partial capability probe, so its actual cost remains unknown; the observed instance tariff was 1.59 USD/hour.

## Preserved evidence

- Progress record: `reports/teacher/train-05-capability-progress.md`
- Versioned gate: `config/capability-probe-gate.json`
- Final teacher configuration: `config/teacher-v3.json`
- Final prompts: `prompts/teacher-prompt-v3.txt` and `prompts/critic-prompt-v4.txt`
- Partial raw artifact: `outputs/train-05/train05-capability-probe-v3-partial.tar.gz`
- Partial artifact SHA-256: `a9efb136656794bdb691ee39867d76c99c8567549859be4ecfa26a5ed3597728`

The partial artifact remains intentionally outside version control. Its checksum is recorded here so a retained local copy can be verified.

## Re-entry criteria

QLoRA may be reconsidered only through a new, explicitly approved decision that:

1. identifies a lawful and technically credible source of training examples;
2. defines an independent quality-control strategy rather than relying on the failed same-checkpoint teacher/critic arrangement;
3. passes a small, pre-registered capability gate before bulk generation or GPU training;
4. demonstrates sufficient human-reviewed grounding, risk coverage, safety, and contract adherence;
5. establishes a curated and versioned TRAIN-06-equivalent dataset before any adapter work or cloud training spend.

Until those conditions are met, the repository history is retained for learning and reproducibility, but QLoRA is not an active roadmap direction.
