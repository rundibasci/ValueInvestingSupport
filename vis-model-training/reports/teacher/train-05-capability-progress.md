# TRAIN-05 — Engine capability progress

## Current assessment

**Status: CLOSED `NO_GO`; QLoRA PROGRAMME PAUSED.**

The pipeline can run, resume, preserve artifacts, recover narrowly defined Markdown-wrapped JSON, support human review, and clean up paid resources. Those are operational achievements, not evidence that the selected engine reliably performs the requested financial-analysis task.

Calibration v2 shows modest semantic improvement over v1, but severe regression in contract adherence and critic behavior. TRAIN-05 therefore remains an engine-capability investigation rather than a dataset-production phase.

## Progress matrix

| Capability | Smoke v1 | Calibration v1 | Calibration v2 | Required | Trend |
|---|---:|---:|---:|---:|---|
| Teacher parseable rate | 100% | 100% | 93% | >=98% | Regressed |
| Teacher structurally valid rate | 100% | 100% | 0% | effectively 100% before curation | Severe regression |
| Critic usable rate after recovery | 100% | 100% | 100% | >=98% | Stable only through recovery |
| Critic canonical JSON rate | 37.5% | 36% | 2.15% | >=95% | Severe regression |
| Critic decisive verdict rate | 0% | 0% | 1.08% | >=90% | No meaningful capability |
| Human accept rate | not measured | 36.67% | 50% | >=80% | Improved, still failing |
| Human grounding average | not measured | 0.867 | 1.367 | >=1.80 | Improved, still failing |
| Human classification average | not measured | 1.233 | 1.800 | >=1.80 | Reached threshold |
| Human risk-coverage average | not measured | 1.233 | 1.200 | >=1.60 | Slight regression |
| Human safety average | not measured | 1.167 | 1.200 | >=1.80 | Essentially flat |

Canonical and recovered critic rates are intentionally separate. Recovery demonstrates that content can often be extracted; it does not demonstrate that the model obeys the required output contract.

## What the engine currently demonstrates

- It usually identifies the broad valuation direction, especially clear fair-value and large positive/negative margin-of-safety cases.
- It can recognize prominent warnings such as insufficient data, stale data, dividend coverage risk, and contradictory signals.
- Human classification improved from calibration v1 to v2.

## What it does not yet demonstrate reliably

- Strict schema adherence: every parseable v2 teacher output added a forbidden `candidateId` property.
- Strict JSON-only behavior: teacher and critic frequently wrap JSON in Markdown; one sampled teacher output was syntactically invalid.
- Independent critic judgment: 92 of 93 usable v2 reviews returned `REVIEW`, only one was decisive.
- Correct thesis invalidation logic: several candidates treated evidence that strengthens a negative thesis as an invalidation condition.
- Evidence discipline for qualitative labels: leverage, payout, dividend attractiveness, resilience, and safety were often asserted without sector, history, or supplied thresholds.
- Conservative escalation: some outputs requested further investigation in prose while setting `humanReviewRequired=false`.
- Complete risk coverage and decision-support safety.

## Interpretation

The selected `google/gemma-3-27b-it` checkpoint has shown partial task understanding, but the present prompting and decoding setup has not shown production-level reliability. The evidence does not yet distinguish conclusively between:

1. a prompt/schema integration defect;
2. a decoding or chat-template defect;
3. a limitation of the checkpoint for strict structured financial reasoning;
4. shared blind spots caused by using the same checkpoint as teacher and critic.

The v2 structural regression is dominated by a systematic extra field, so another full 50-scenario cloud calibration would be premature before isolating the integration defects.

## Next evidence gate

No new full calibration and no bulk run until all of the following pass on a small probe:

1. Contract tests confirm the rendered prompt and schema agree about `candidateId` ownership.
2. Teacher produces strict JSON with no Markdown fence and no additional properties on at least 20/20 deterministic probe slots.
3. Critic produces canonical JSON on at least 19/20 reviews.
4. Critic returns decisive `ACCEPT` or `REJECT` on at least 18/20 deliberately clear cases.
5. Targeted cases cover inverted invalidation conditions, weak margin of safety, stale/inconsistent data, unsupported thresholds, and `humanReviewRequired` consistency.
6. Human spot review reaches at least 80% acceptance and required average scores.

If the checkpoint fails this probe after one prompt/template correction cycle, stop iterating prompts and run a controlled model-comparison decision rather than spending on another full calibration.

## Latest gate

- Calibration v2 decision: `NO_GO`.
- Failed criteria: parseable rate, canonical critic rate, decisive critic rate, human accept rate, grounding, risk coverage, and decision-support safety.
- Bulk started: no.
- Automatic training promotion: disabled.
- Paid resources remaining: none.

## Corrective cycle v3 status

- Model-visible teacher payload no longer contains `candidateId`; pipeline identity remains in provenance only.
- Model-visible critic payload no longer contains `candidateId`.
- Configured decoding parameters are now honored by the Hugging Face backend.
- `teacher-v3.json` uses greedy decoding (`doSample=false`).
- Teacher prompt v3 adds a strict key whitelist, review-consistency rule, and correct invalidation-direction rules.
- Critic prompt v4 makes `REVIEW` exceptional and requires decisive verdicts on clear cases.
- A deterministic 10-scenario/20-slot capability probe and versioned gate are implemented.
- Local fake dry run passes orchestration: 20/20 schema-valid candidates and 20/20 canonical decisive critic reviews.
- Real checkpoint probe executed on 2026-08-24 and stopped early after 5/5 outputs were `PARSE_REJECTED`.

## Capability probe v3 result

- Provider/hardware: RunPod Secure Cloud, NVIDIA A100-SXM4-80GB, 1.59 USD/hour.
- Planned scope: 10 scenarios, 20 teacher slots, up to 20 critic reviews.
- Early-stop scope: 5 teacher slots; critic not started.
- Result: 5/5 raw responses wrapped otherwise JSON-looking content in Markdown `json` fences.
- Candidate status: 5 `PARSE_REJECTED`, zero canonical JSON, zero schema-eligible outputs.
- Output lengths: 579–604 tokens; failure was not early truncation.
- Gate implication: the required 20/20 parseable and structurally valid rate became impossible after the first failure.
- Decision: stop immediately; do not retry, recover into a success metric, start critic, or launch calibration v3.
- Artifact: `outputs/train-05/train05-capability-probe-v3-partial.tar.gz`.
- Artifact SHA-256: `a9efb136656794bdb691ee39867d76c99c8567549859be4ecfa26a5ed3597728`.
- Resource cleanup: pod deleted; final pod and network-volume lists are empty.

The systematic fence behavior persisted after removing model-visible identifiers, strengthening the prompts, and switching to greedy decoding. Prompt-only iteration reached the predefined stop condition. On 2026-08-24 the project decided not to run a model comparison now and to pause the downstream QLoRA programme. Any replacement architecture or renewed training path requires a separate explicit decision.

Closure rationale and re-entry criteria: `train-05-failure-and-qlora-pause.md`.

Sources: `train-05-smoke-summary-v1.json`, `train-05-calibration-summary-v1.json`, `train-05-calibration-human-review-summary-v1.json`, local calibration-v2 report/review/gate artifacts, and `config/calibration-v2-gate.json`.
