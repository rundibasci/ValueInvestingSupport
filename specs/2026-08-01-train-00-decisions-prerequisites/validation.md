# TRAIN-00 — Validation

## Acceptance Criteria

- [x] The student decision names `google/gemma-3-4b-it` and requires an immutable revision pin before use.
- [x] The teacher decision names `google/gemma-3-27b-it`, records its rationale, and defines fallback criteria.
- [x] Official Gemma terms, prohibited-use policy, model cards, access date, derivative classification, and distribution obligations are recorded.
- [x] Dataset sources and current synthetic examples have an explicit provenance and licence basis.
- [x] Commercial distribution and hosted-service use are flagged for external legal review.
- [x] The local hardware report contains observed facts only and explains the absence of CUDA/NVIDIA support.
- [x] An external single-GPU reference profile is documented without claiming feasibility that TRAIN-07 has not measured.
- [x] The data policy prohibits secrets, personal data, unauthorized proprietary data, untraceable claims, impermissible teacher outputs, and substantial copyrighted text.
- [x] A secret-hygiene review covers all current TRAIN files and relevant ignore rules, with sanitized evidence.
- [x] No model weights, external infrastructure, paid service, hosted inference, or training run is created by TRAIN-00.
- [x] The README accurately distinguishes completed TRAIN-00 work from exploratory/incomplete TRAIN-01 and TRAIN-02 work.
- [x] The final TRAIN-00 decision is explicit and its remaining external conditions are visible.

## Documentation Test Matrix

| Scenario | Expected result |
|---|---|
| Model selection review | Student and teacher IDs, roles, revision policy, rationale, and fallback are unambiguous |
| Gemma output used as synthetic training data | Classified and governed as a Gemma Model Derivative workflow under the reviewed terms |
| Adapter distribution | Notice, terms, use restrictions, modified-file notices, and legal-review requirements are discoverable |
| Hosted internal evaluation | Access controls and applicable terms remain documented; no claim of production approval |
| Local Apple M4 development | Validation/documentation allowed; CUDA QLoRA capability is not asserted |
| External NVIDIA environment | Planning requirements are recorded; TRAIN-07 must prove actual compatibility and VRAM use |
| Missing hardware datum | Marked `not observed`, never guessed |
| Secret-like finding | Value is not printed or committed; finding is remediated or explicitly blocked |
| Teacher output candidate | Retains provenance and remains excluded from release data until validation and human review |

## Regression Checks

- [x] Existing TRAIN-01 schemas, examples, prompt, and JSONL dataset remain unchanged unless a documented correction is required.
- [x] `node vis-model-training/scripts/validate-dataset.mjs` passes.
- [x] No application runtime, valuation, score, portfolio, or recommendation code changes.
- [x] The MiFID II decision-support boundary remains explicit.
- [x] Repository status contains no generated weights, adapters, caches, tokens, or raw teacher outputs.

## Verification Commands

```bash
node vis-model-training/scripts/validate-dataset.mjs
git diff --check
git status --short
git ls-files vis-model-training
```

Secret scanning will use an installed repository scanner when available, plus a targeted tracked-file pattern check. Reports must never echo matched secret values.

## Manual Validation

1. Verify each official external reference opens and corresponds to the recorded title/version.
2. Confirm the student and teacher model repositories require the expected licence acceptance.
3. Compare the hardware report with current host output and ensure unavailable memory data is not inferred.
4. Review the licence register with the owner responsible for commercial/legal approval.
5. Confirm TRAIN-01 and TRAIN-02 remain blocked from completion by their documented outstanding criteria.

## Merge Gate

- All acceptance criteria above are checked or an explicit NO-GO is recorded.
- Documentation is internally consistent and cites primary official sources.
- Secret-hygiene checks have no unresolved high-confidence finding.
- Existing dataset validation and `git diff --check` pass.
- No model execution, training, paid infrastructure, or runtime VIS integration has occurred.
- The user has approved this specification before implementation begins.
