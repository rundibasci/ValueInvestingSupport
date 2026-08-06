# Gemma 3 4B IT — TRAIN-03 Error Analysis

Status: complete; automatic analysis and human review recorded.

## Canonical Run

- Provider: RunPod Secure Cloud
- GPU: NVIDIA L4, 24 GB
- Precision: BF16
- Model: `google/gemma-3-4b-it`
- Model revision: `093f9f388b31de276ce2de164bdc2081324b9767`
- Prompt: `system-prompt-v2.txt`
- Inference commit: `9f82276`
- Cases: 50 unique IDs
- Generation errors: 0
- Average latency: 26,937.518 ms
- Average output length: 1,009.4 characters

Prompt v1 smoke outputs are excluded. Prompt v1 referred to an output schema that was not present in model context; prompt v2 supplied the complete contract before the canonical run.

## Strict Metrics

| Metric | Result |
|---|---:|
| JSON validity | 0% |
| Schema compliance | 0% |
| Classification accuracy | 0% |
| Exact field coverage | 0% |

All 50 first outputs wrapped an otherwise recoverable JSON object in a Markdown fence and appended `<end_of_turn>`. The strict result remains a failure because the prompt explicitly prohibited both wrappers. Stored outputs were not rewritten or retried.

## Recoverable Diagnostics

The diagnostic parser strips only the exact observed ````json ... ```<end_of_turn>` wrapper. These figures do not change strict metrics.

| Metric | Result |
|---|---:|
| Wrapped JSON recovered | 50/50 |
| Recovered schema compliance | 100% |
| Classification accuracy | 34% |
| Evidence-field precision | 45.977% |
| Human-review accuracy | 86% |
| Exact field coverage | 100% |
| Unsupported numeric claim rate | 8% |
| Prohibited recommendation rate | 0% |

### Classification by category

| Category | Accuracy | Human-review accuracy | Evidence precision |
|---|---:|---:|---:|
| Robust undervaluation | 100% | 100% | 48.0% |
| Value trap | 0% | 0% | 100% |
| Overvaluation | 0% | 100% | 50.0% |
| Fair value | 100% | 100% | 52.632% |
| Dividend at risk | 0% | 100% | 29.412% |
| Insufficient data | 100% | 100% | N/A |
| Stale data | 0% | 100% | 0% |
| Contradictions | 0% | 100% | 26.087% |
| Adversarial | 0% | 100% | 25.0% |

## Principal Error Classes

1. **Output envelope violation.** Every result used Markdown and emitted `<end_of_turn>`, making strict JSON validity zero despite structurally valid inner objects.
2. **Valuation bias dominates safety states.** Positive margin of safety frequently forced `POTENTIALLY_UNDERVALUED` even for value traps, partial data, stale data, contradictions and adversarial warnings.
3. **Overvaluation direction is unreliable.** All overvaluation cases were classified as potentially undervalued despite market price exceeding intrinsic value and a negative margin of safety.
4. **Dividend-risk evidence is underused.** Dividend-at-risk outputs focused on valuation and declining free cash flow while omitting the supplied excessive payout ratio and required `UNDER_REVIEW` classification.
5. **Review logic is incomplete.** Stale, inconsistent, partial and adversarial cases generally set the review boolean correctly, but value traps with strongly declining trends failed review in every case.
6. **Grounding quality is uneven.** Unsupported numbers occurred in 8% of recoverable outputs. Several narratives treated assumptions as facts or described moderate leverage as elevated without a supplied threshold.
7. **Adversarial instruction resistance is partial but promising.** No prohibited buy/sell/hold recommendation was emitted and the injected warning was not followed, but the classification still ignored the required review state.

## Measurable Improvement Targets

The following targets are fixed before training and will be evaluated again in TRAIN-09:

1. Raise strict JSON validity from 0% to at least 95%, with no Markdown or trailing special-token text.
2. Raise overall classification accuracy from 34% to at least 80%, with at least 80% in each of value trap, overvaluation, stale, contradictions and adversarial categories.
3. Raise evidence-field precision from 45.977% to at least 85% and reduce unsupported numeric claims from 8% to at most 1%.
4. Raise human-review accuracy from 86% to at least 95%, including 100% on value-trap, insufficient, stale, inconsistent and adversarial cases.
5. Preserve a 0% prohibited recommendation rate.

## Manual Review Gate

The deterministic sample contains 20 cases and covers all nine categories. Reviewer alias `marcello` completed the review on 2026-08-06.

| Review result | Value |
|---|---:|
| Accepted cases | 3/20 (15%) |
| Summary correctness | 1.00/2 |
| Bull/bear balance | 1.20/2 |
| Risk quality | 0.90/2 |
| Input adherence | 0.80/2 |
| Reviewer utility | 0.85/2 |

The human review confirms the automatic error taxonomy. In particular, payout ratio 120% was ignored in both dividend-risk samples even though it is a strong alarm that must affect classification; both overvaluation samples reversed valuation direction; both value-trap samples failed to request human review; and unsupported threshold judgments appeared repeatedly for leverage, yield and value score.
