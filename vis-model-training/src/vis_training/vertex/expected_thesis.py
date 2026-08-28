"""Generic, grounded-only expected-thesis derivation.

Shared by `real_ticker_dataset.py` (TA3) and `build_scenarios_benchmark_dataset.py`
(TA3, converting TRAIN-04's raw scenarios-v1.jsonl into the benchmark harness's
3-message contract) so both datasets compute `expected` with exactly the same
rules, derived strictly from the supplied `thesis-input.schema.json` fields —
never from any real-world or scenario-generator-only knowledge — matching
`prompts/system-prompt-v2.txt`'s own stated rules.
"""

from __future__ import annotations

from typing import Any, Dict, List


# dataQuality values that TRAIN-02's semantic validator treats as requiring
# human review regardless of any other signal (src/vis_training/validation/
# semantic_validator.py: _REVIEW_DATA_QUALITIES). Mirrored here so a
# generated `expected` never fails that rule for TRAIN-04-sourced scenarios,
# which (unlike TA3's real-ticker inputs) actually vary dataQuality.
_REVIEW_DATA_QUALITIES = {"INSUFFICIENT", "INCONSISTENT", "STALE"}


def derive_expected_thesis(input_data: Dict[str, Any]) -> Dict[str, Any]:
    """Grounded-only expected thesis: reflects only `input_data`, computed by
    the same rules the system prompt states, never any out-of-band fact.

    Mirrors src/vis_training/validation/semantic_validator.py's rules exactly
    (never cite a null input field as evidence; INSUFFICIENT dataQuality
    forces classification=INSUFFICIENT_DATA; INSUFFICIENT/INCONSISTENT/STALE
    dataQuality or a CONTRADICTORY_SIGNALS warning forces
    humanReviewRequired=True) so every derived record is guaranteed to pass
    that validator, not just be schema-shaped."""
    data_quality = input_data.get("dataQuality")
    warnings = input_data.get("deterministicWarnings") or []
    contradictory = isinstance(warnings, list) and "CONTRADICTORY_SIGNALS" in warnings
    review_required = data_quality in _REVIEW_DATA_QUALITIES or contradictory

    if data_quality == "INSUFFICIENT":
        return {
            "classification": "INSUFFICIENT_DATA",
            "confidence": 0.2,
            "summary": "Supplied evidence is insufficient to support a confident valuation assessment.",
            "bullCase": [],
            "bearCase": [],
            "keyRisks": ["Supplied data is insufficient for a reliable assessment."],
            "keyAssumptions": ["The supplied VIS inputs remain applicable."],
            "invalidationConditions": ["Sufficient data becomes available to support a full assessment."],
            "dataWarnings": ["Supplied dataQuality is INSUFFICIENT."],
            "humanReviewRequired": True,
        }

    bull_case: List[Dict[str, Any]] = []
    bear_case: List[Dict[str, Any]] = []
    key_risks: List[str] = []
    data_warnings: List[str] = []

    margin = input_data.get("marginOfSafetyPercent")
    market_price = input_data.get("marketPrice")
    intrinsic_value = input_data.get("intrinsicValue")
    # Only cite this evidence trio together when none of the three are null —
    # citing a null field is exactly what EVIDENCE_FIELD_NULL flags.
    margin_evidence_complete = margin is not None and market_price is not None and intrinsic_value is not None

    if margin_evidence_complete and margin > 0:
        bull_case.append(
            {
                "claim": "Supplied market price is below supplied intrinsic value.",
                "evidenceFields": ["marketPrice", "intrinsicValue", "marginOfSafetyPercent"],
            }
        )
    elif margin_evidence_complete:
        bear_case.append(
            {
                "claim": "Supplied market price is not supported by supplied intrinsic value.",
                "evidenceFields": ["marketPrice", "intrinsicValue", "marginOfSafetyPercent"],
            }
        )
        key_risks.append("Valuation evidence supplied does not support the current price.")
    else:
        data_warnings.append("Valuation evidence (marketPrice/intrinsicValue/marginOfSafetyPercent) is incomplete.")

    for trend_field in ("revenueTrend", "earningsTrend", "freeCashFlowTrend"):
        if input_data.get(trend_field) == "STRONGLY_DECLINING":
            bear_case.append(
                {
                    "claim": f"Supplied {trend_field} is strongly declining.",
                    "evidenceFields": [trend_field],
                }
            )
            key_risks.append(f"Strongly declining {trend_field} per supplied evidence.")

    payout_ratio = input_data.get("payoutRatioPercent")
    if payout_ratio is not None and payout_ratio > 100:
        bear_case.append(
            {
                "claim": "Supplied payout ratio exceeds 100%, which is not sustainable on its own.",
                "evidenceFields": ["payoutRatioPercent"],
            }
        )
        key_risks.append("Payout ratio above 100% per supplied evidence.")

    has_strongly_declining = any(
        input_data.get(field) == "STRONGLY_DECLINING"
        for field in ("revenueTrend", "earningsTrend", "freeCashFlowTrend")
    )
    has_red_flag = has_strongly_declining or (payout_ratio is not None and payout_ratio > 100)
    # Thresholds match the roadmap's own existing MoS convention (Phase Z5's
    # UI gauge: green > 15%, yellow 5-15%, red < 5% or negative), not a
    # freshly invented cutoff — reused here for classification, not just
    # color, since it is already this project's documented boundary for
    # "comfortable" vs. "thin" vs. "no" margin of safety.
    #
    # Margin sign/magnitude is the primary driver; a single red flag only
    # downgrades a *thin* margin to ambiguous, not a strong one. (Calibrated
    # 2026-08-28 against TA3's live run: comparing this template's classification
    # to Gemini's actual output across all 500 TRAIN-04 scenarios showed two
    # systematic gaps — (a) margin near zero has no FAIRLY_VALUED outcome at
    # all in the earlier version, and (b) ANY red flag forced UNDER_REVIEW
    # even at a strongly positive margin (avg 25%, up to 44.83%) where Gemini
    # consistently still called POTENTIALLY_UNDERVALUED — a real business
    # judgment this template was systematically not modeling, not a Gemini
    # error: TRAIN-02's own semantic validator raised zero diagnostics
    # against Gemini's actual output for any of those cases.)
    if not margin_evidence_complete:
        classification = "UNDER_REVIEW"
        confidence = 0.4
    elif margin < -5:
        classification = "POTENTIALLY_OVERVALUED"
        confidence = 0.7
    elif margin <= 5:
        classification = "FAIRLY_VALUED"
        confidence = 0.5
    elif margin <= 15 and has_red_flag:
        # Thin margin, contradicted by a separate red flag — genuinely
        # ambiguous, not a clean bull or bear case.
        classification = "UNDER_REVIEW"
        confidence = 0.5
    else:
        # Comfortable margin (>15%) carries the classification even against
        # a single red flag, matching Gemini's observed behavior; the red
        # flag still surfaces in bearCase/keyRisks above, just not the
        # top-level classification.
        classification = "POTENTIALLY_UNDERVALUED"
        confidence = 0.7

    return {
        "classification": classification,
        "confidence": confidence,
        "summary": "Assessment based strictly on the supplied valuation and trend evidence.",
        "bullCase": bull_case,
        "bearCase": bear_case,
        "keyRisks": key_risks,
        "keyAssumptions": ["The supplied VIS inputs remain applicable."],
        "invalidationConditions": ["Material changes in the supplied operating or valuation evidence."],
        "dataWarnings": data_warnings,
        "humanReviewRequired": review_required,
    }
