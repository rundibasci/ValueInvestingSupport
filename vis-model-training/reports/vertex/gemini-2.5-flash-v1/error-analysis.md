# Vertex AI Gemini 2.5 Flash — TA3 Capability Benchmark & Comparison to the Closed Gemma Baseline

Status: live run complete, human review complete (68/574 cases). **Gate evaluation partial** — see "Gate Fields Left Unresolved" below. `specs/roadmap.md` → Phase TA3 is not yet marked complete; this report is the evidence base for that decision, not the decision itself.

## Canonical Run

- Provider: Vertex AI, `europe-west1`, project `vis-version0`
- Model: `gemini-2.5-flash`, `temperature=0.0`, `thinkingBudget=0`
- SDK: `google-genai==1.47.0`
- Prompt: `system-prompt-v2.txt` (unchanged from TRAIN-03/TA2)
- Cases: 574 (50 TRAIN-03 `base-benchmark-v1` + 500 TRAIN-04 `scenarios-v1` converted + 24 TA3 `real-ticker-knowledge-leakage-v1`)
- Generation errors: 0
- Average latency: ~2.9s/case (vs. Gemma's ~27–35s/case)
- Estimated cost: $0.84 (see `cost.json`)

Three request-shape defects were found and fixed via live smoke tests before the canonical run (see `specs/2026-08-27-ta3-vertex-capability-benchmark/validation.md` → "Live Run" for the full sequence): `uniqueItems` unsupported by Vertex's `responseSchema`; bare `enum` nodes need an explicit `type`; `gemini-2.5-flash`'s default "thinking" consumed the output budget before `thinkingBudget:0` was set.

## Strict Metrics — Gemini vs. the Closed Gemma Baseline

| Metric | Gemma (strict, TRAIN-03) | Gemma (recovered¹) | **Gemini (this run)** |
|---|---:|---:|---:|
| JSON validity | 0% | 100% | **100%** |
| Schema compliance | 0% | 100% | **100%** |
| Classification accuracy² | 0% | 34% | **52–81%²** |
| Evidence-field precision | 0% | 45.977% | 21–41%² |
| Human-review accuracy | — | 86% | 66–93%² |
| Unsupported numeric claim rate | — | 8% | **0.6%** (TRAIN-04) |
| Prohibited recommendation rate | — | 0% | **0%** (5 mechanical flags, all verified false positives) |

¹ Gemma's strict JSON validity was 0% because every output wrapped valid JSON in a Markdown fence plus a trailing `<end_of_turn>` token; "recovered" strips that exact wrapper. Gemini needed no such recovery — every one of 574 outputs was `json.loads`-valid on the first parse, no post-processing.

² Ranges reflect the three datasets (real-ticker 20 cases / base-benchmark 50 cases / scenarios 500 cases) — see `metrics.json` for exact per-dataset figures. **Classification accuracy and evidence-field precision are measured against this project's own template-derived `expected` for two of the three datasets (real-ticker, scenarios), not a human-authored gold answer** — a materially different comparison basis than Gemma's, whose 34%/45.977% were scored against TRAIN-03's hand-authored 50-case gold set. `base-benchmark-v1`'s Gemini numbers *are* against that same TRAIN-03 hand-authored gold set and are the most directly comparable to Gemma's row above.

### Classification by category (`base-benchmark-v1`, same 50 hand-authored TRAIN-03 cases Gemma was scored against)

| Category | Gemma accuracy | Gemma HR accuracy | **Gemini accuracy** | **Gemini HR accuracy** |
|---|---:|---:|---:|---:|
| Robust undervaluation | 100% | 100% | **100%** | **100%** |
| Value trap | 0% | 0% | 0%³ | **0%** |
| Overvaluation | 0% | 100% | **100%** | **100%** |
| Fair value | 100% | 100% | **100%** | **100%** |
| Dividend at risk | 0% | 100% | 0%³ | **0%** |
| Insufficient data | 100% | 100% | **100%** | **100%** |
| Stale data | 0% | 100% | 0%³ | **100%** |
| Contradictions | 0% | 100% | 60% | **100%** |
| Adversarial | 0% | 100% | 0%³ | **0%** |

³ Gemini's "0%" classification accuracy on value-trap/dividend-at-risk/stale-data/adversarial does **not** mean Gemini's answer is arbitrary or wrong the way Gemma's markdown-wrapped near-misses were — see "Key Finding" below. Gemma failed these categories differently: it frequently ignored the negative margin of safety entirely (see Gemma's own error analysis, "Overvaluation direction is unreliable" / "Valuation bias dominates safety states"). Gemini gets the *direction* right in every one of these categories (correctly identifies deteriorating fundamentals in its own `bearCase`) but chooses a different top-level `classification` label than TRAIN-03's `UNDER_REVIEW` gold answer, landing on `POTENTIALLY_UNDERVALUED`/`POTENTIALLY_OVERVALUED` instead — a labeling/calibration gap, not a grounding failure.

## Key Finding: `humanReviewRequired` does not respond to fundamental deterioration alone

The single most consequential finding of this run, found by comparing Gemini's actual output (not any template) across the full 574-case corpus:

**Across every case with a `STRONGLY_DECLINING` trend that is not already flagged via `dataQuality`/`CONTRADICTORY_SIGNALS`, `humanReviewRequired` was `false` in 100% of cases (68/68 across TRAIN-04's `VALUE_TRAP`/`FCF_DETERIORATION`/`DIVIDEND_RISK` categories).** The same pattern holds on TRAIN-03's hand-authored gold set: `VALUE_TRAP`, `DIVIDEND_AT_RISK`, and `ADVERSARIAL` all show `humanReviewRequired=false` despite Gemini's own `bearCase` correctly listing the declining fundamentals. By contrast, `STALE_DATA` and `CONTRADICTIONS`/`INCONSISTENT_DATA` reliably get `humanReviewRequired=true` — the flag responds cleanly to explicit *data-quality* problems, just not to fundamental *business* deterioration by itself.

This is exactly the "cheap stock, deteriorating fundamentals" pattern (a value trap) this platform's mission exists to catch — a real user reading only the top-level classification and review flag would not be prompted to look closer. Confirmed via manual review: 13 of the 68 human-reviewed cases (6 on `base-benchmark-v1`, 7 on `scenarios-v1`) were marked `accepted: false` for exactly this reason — see `manual-review.json`.

This is real Gemini behavior on the real, unmodified system prompt — not a template or harness defect, and not something this pass attempts to fix (`prompts/system-prompt-v2.txt` is out of scope for TA3 per `plan.md`). It is the leading candidate for TA4 prompt tuning if a production decision is made to proceed.

## Manual Review Gate

68 cases reviewed (`manual-review.json`), covering 20 distinct benchmark categories (well above the roadmap's minimum of 20 cases / 10 categories). Reviewer alias `marcellomazzoni`, reviewed 2026-08-28.

| Review result | Gemma (TRAIN-03, 20 cases) | **Gemini (this run, 68 cases)** |
|---|---:|---:|
| Accepted cases | 3/20 (15%) | **55/68 (80.9%)** |
| Summary correctness | 1.00/2 | **1.81/2** |
| Bull/bear balance | 1.20/2 | **1.91/2** |
| Risk quality | 0.90/2 | 1.32/2 |
| Input adherence | 0.80/2 | **1.71/2** |
| Reviewer utility | 0.85/2 | **1.77/2** |
| Knowledge leakage (real-ticker only, n/a for Gemma) | — | **2.00/2 (20/20, zero leakage observed)** |

`riskQuality` is the smallest gain and the only dimension still below 1.5/2 — driven by a recurring pattern (not a category-specific failure): `keyRisks` consistently mixes 1–2 well-grounded items with 1–2 generic, not-input-derived ones ("regulatory changes," "currency fluctuations," "changes in consumer preferences") — plausible in the abstract but not traceable to any supplied field, a soft violation of the system prompt's "never invent risks" rule for the free-text fields (`keyRisks`/`keyAssumptions`/`invalidationConditions`) that lack an `evidenceFields` requirement.

**The accept-rate margin is thin relative to the gate.** `capability-probe-gate.json`'s `minimumHumanAcceptRate` is 0.80; the actual combined rate is 0.8088 — three additional "not accepted" cases in the value-trap/deterioration-blind-spot cluster would fail it.

## TRAIN-02 Semantic Validator Run Against Gemini's Actual Output (not the template)

`validate_semantics` (the same function `scripts/validate_dataset.py` uses) was run against all 574 real `parsedOutput` records — a genuine correctness check, not template comparison. 12/574 flagged; all 12 manually verified as false positives of the mechanical rule, not real defects:
- 5 `PROHIBITED_RECOMMENDATION`: Gemini correctly *warning* that an injected "buy recommendation" is untrusted, not giving one — the regex can't distinguish citing a prohibited phrase from using it.
- 3 `UNSUPPORTED_NUMERIC_CLAIM`: mostly the schema-required `confidence` field caught by the numeric scanner; one borderline generic leverage threshold.
- 4 `EVIDENCE_FIELD_NULL` (13 occurrences): Gemini citing a null field specifically to say the data is *missing*, not fabricating a value — correct decision-support behavior the validator rule doesn't yet distinguish.

**Zero genuine grounding/safety violations found across the entire live-generated corpus.**

## Gate Fields Left Unresolved (`config/capability-probe-gate.json`)

Mechanically computable fields all pass (see `specs/2026-08-27-ta3-vertex-capability-benchmark/validation.md` for the full table: `minimumParseableRate`, `minimumStructuralValidRate`, `minimumHumanReviewCount`, `minimumHumanCategoryCount`, `minimumHumanAcceptRate` (0.8088, thin margin), `minimumRealTickerKnowledgeLeakageCaseCount`, `minimumKnowledgeLeakageAcceptRate` (1.0), `maximumValidatorFalsePositiveRate` (0.021)).

Two groups of gate fields do **not** map cleanly onto this pipeline and were **not** evaluated — an explicit decision is still needed, not silently resolved by this report:
1. **`minimumAverageScores`** (`grounding`/`classification`/`riskCoverage`/`decisionSupportSafety`) — names don't correspond 1:1 to this rubric's five dimensions.
2. **`minimumUsableCriticRate`/`minimumCanonicalCriticRate`/`minimumDecisiveCriticRate`/`expectedCandidateSlots`** — TRAIN-05 critic-pipeline concepts with no equivalent in TA3's design (no separate critic-review step).

## Recommendation Basis for TA4 (not a decision made by this report)

- **Structural/grounding capability gap is closed decisively.** 100% JSON validity and schema compliance vs. Gemma's 0%; zero genuine semantic-validator violations across 574 real cases; zero knowledge-leakage observed across 20 scored real-ticker cases.
- **The one clear, actionable gap is the `humanReviewRequired`/value-trap blind spot** — a single systematic pattern behind 13/68 non-accepted reviews, not a scattered collection of unrelated defects. A candidate prompt-tuning target for TA4, testable against this same 68-case review set as a regression check.
- **`riskQuality`'s generic-filler pattern** is a secondary, lower-severity finding — likely addressable with a small prompt clarification requiring every `keyRisks`/`keyAssumptions` entry to trace to a specific supplied field, same as `bullCase`/`bearCase` already requires via `evidenceFields`.
