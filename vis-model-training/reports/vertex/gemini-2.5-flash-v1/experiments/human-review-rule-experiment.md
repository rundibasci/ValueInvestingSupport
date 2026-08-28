# Experiment: one-line prompt fix for the `humanReviewRequired`/value-trap blind spot

Status: **experimental candidate, not adopted.** `prompts/system-prompt-v2.txt` (production) is unchanged. This experiment used a separate variant file (`prompts/system-prompt-v2-ta3-experiment-hr.txt`) never referenced by `config/vertex-gemini-v1.json` or any committed dataset/config. Out of scope for TA3 by explicit user decision (2026-08-28) — recorded here as evidence for a future TA4 decision, not applied.

## Hypothesis

`error-analysis.md`'s key finding traces to a specific, narrow gap in `prompts/system-prompt-v2.txt`'s Rule 9, which enumerates exactly when `humanReviewRequired` must be `true`: `dataQuality` STALE/INCONSISTENT/INSUFFICIENT, or `CONTRADICTORY_SIGNALS`. It does not mention a `STRONGLY_DECLINING` trend at all — Rule 10 only requires a bear-case claim for that, not the review flag. Gemini follows Rule 9 exactly as written; the gap is in the rule, not in prompt adherence.

## Change tested

Rule 9 extended with one clause:

```diff
- 9. Set `humanReviewRequired` to true when `dataQuality` is `STALE`, `INCONSISTENT`, or `INSUFFICIENT`, or when `deterministicWarnings` contains `CONTRADICTORY_SIGNALS`.
+ 9. Set `humanReviewRequired` to true when `dataQuality` is `STALE`, `INCONSISTENT`, or `INSUFFICIENT`, when `deterministicWarnings` contains `CONTRADICTORY_SIGNALS`, or when a `STRONGLY_DECLINING` revenue, earnings, or free-cash-flow trend is present alongside a positive `marginOfSafetyPercent` (a classic value-trap pattern: cheap on paper, deteriorating in practice).
```

Deliberately narrow: only fires when a trend is *strongly* declining (not merely `DECLINING`) *and* the margin of safety is positive (a negative margin already signals caution via the classification itself, so the value-trap-specific risk — "looks cheap, isn't" — doesn't apply).

## Method

Live re-run (Vertex AI, same model/config, `google-genai`) of 19 cases against the modified prompt only:
- All 13 cases marked `accepted: false` in `manual-review.json` (the direct evidence base for the original finding).
- 6 control cases already behaving correctly (`humanReviewRequired` either correctly `false` with no decline, or correctly `true` via the pre-existing data-quality rules) — to check the new clause doesn't over-trigger.

## Result

| Rule condition (`STRONGLY_DECLINING` + margin > 0) | Cases | `humanReviewRequired` after fix |
|---|---:|---|
| Met | 6 (`VIS-BENCH-0008/0009`, `SCN-000334/260/301/302`) | **True in 6/6 (100%)** |
| Not met (moderate `DECLINING` only, or thin/negative margin) | 7 | Unchanged — as expected, the narrow clause doesn't apply |
| Control (no decline / pre-existing rule) | 6 | **Unchanged, zero false positives** |

Full per-case output: `experiments/human-review-rule-experiment-results.json`.

**100% reliability where the clause's exact condition holds; zero regressions on controls.** The 7 unresolved cases (`VIS-BENCH-0026/0027/0046/0047`, `SCN-000476/259`, plus one more) reflect a genuinely broader, softer pattern — moderate (not strong) decline, or thin/negative margin — that this narrowly-scoped clause deliberately does not attempt to cover, to avoid over-triggering review on routine risk disclosure.

## Estimated effect on the gate margin (not measured — projected)

The 6 resolved cases were marked `accepted: false` specifically because the missing review flag undermined `summaryCorrectness`/`reviewerUtility` scoring. If this fix is adopted and the review is redone, the accept rate would plausibly move from **55/68 (0.8088)** to roughly **61/68 (0.897)** — a materially safer margin above `capability-probe-gate.json`'s `minimumHumanAcceptRate` (0.80) floor. This is a projection based on the scoring rationale already on record, not a re-scored result — a real re-review would be needed to confirm it.

## Cost

19 live Vertex AI calls, Flash tier — well under $0.05.

## What would be needed to actually adopt this

1. Apply the change to the real `prompts/system-prompt-v2.txt` (a decision this experiment deliberately did not make).
2. Re-run the full 574-case corpus (real spend, ~$0.84 based on this session's run) to confirm no regression elsewhere in the prompt's behavior — this experiment only touched 19 cases.
3. Redo the human review (or at minimum, re-review the previously-flagged cases) against the new outputs.
4. Re-apply the gate.
5. Decide separately whether the remaining 7-case broader pattern (moderate decline / thin margin) also needs addressing, or is an acceptable residual gap.
