"""Deterministic offline teacher and critic test doubles."""

import json
from typing import Any, Dict, Iterable, Optional


def expected_thesis(scenario: Dict[str, Any]) -> Dict[str, Any]:
    scenario_type = scenario["scenarioType"]
    data = scenario["input"]
    review_types = {"VALUE_TRAP", "DIVIDEND_RISK", "HIGH_LEVERAGE", "CONTRADICTORY_SIGNALS", "STALE_DATA", "INCONSISTENT_DATA", "ADVERSARIAL_INPUT"}
    if scenario_type == "OVERVALUED_STRONG":
        classification, review = "POTENTIALLY_OVERVALUED", False
    elif scenario_type == "INSUFFICIENT_DATA":
        classification, review = "INSUFFICIENT_DATA", True
    elif scenario_type == "FAIR_VALUE":
        classification, review = "FAIRLY_VALUED", False
    elif scenario_type in review_types or scenario_type == "FCF_DETERIORATION":
        classification, review = "UNDER_REVIEW", True
    else:
        classification, review = "POTENTIALLY_UNDERVALUED", False

    bull, bear, risks = [], [], []
    if data.get("intrinsicValue") is not None and data.get("marginOfSafetyPercent") is not None:
        if data["marginOfSafetyPercent"] > 0:
            bull.append({"claim": "Price is below the supplied intrinsic value.", "evidenceFields": ["marketPrice", "intrinsicValue", "marginOfSafetyPercent"]})
        elif data["marginOfSafetyPercent"] < 0:
            bear.append({"claim": "Price exceeds the supplied intrinsic value.", "evidenceFields": ["marketPrice", "intrinsicValue", "marginOfSafetyPercent"]})
    declining = [field for field in ("revenueTrend", "earningsTrend", "freeCashFlowTrend") if data.get(field) in {"DECLINING", "STRONGLY_DECLINING", "VOLATILE"}]
    if declining:
        bear.append({"claim": "Supplied operating evidence requires caution.", "evidenceFields": declining})
    if scenario_type == "DIVIDEND_RISK":
        bear.append({"claim": "Payout and cash flow evidence indicate dividend risk.", "evidenceFields": ["payoutRatioPercent", "freeCashFlowTrend"]})
        risks.append("Dividend sustainability requires review.")
    if scenario_type == "HIGH_LEVERAGE":
        bear.append({"claim": "Leverage requires contextual review.", "evidenceFields": ["netDebtToEbitda", "deterministicWarnings"]})
    if data.get("dataQuality") in {"STALE", "INCONSISTENT", "INSUFFICIENT", "PARTIAL"}:
        risks.append("Data quality requires human review.")
    return {
        "classification": classification,
        "confidence": 0.75,
        "summary": "The supplied synthetic evidence supports a cautious, reviewable thesis.",
        "bullCase": bull,
        "bearCase": bear,
        "keyRisks": risks,
        "keyAssumptions": ["The supplied VIS inputs remain applicable."],
        "invalidationConditions": ["Material changes in the supplied evidence require a new assessment."],
        "dataWarnings": list(data.get("deterministicWarnings", [])),
        "humanReviewRequired": review,
    }


class FakeTeacherBackend:
    def __init__(self, *, invalid_json_ids: Iterable[str] = (), failure_ids: Iterable[str] = (), overrides: Optional[Dict[str, Dict[str, Any]]] = None):
        self.invalid_json_ids = set(invalid_json_ids)
        self.failure_ids = set(failure_ids)
        self.overrides = overrides or {}
        self.calls = []

    def generate(self, messages, *, seed: int, max_new_tokens: int):
        payload = json.loads(messages[-1]["content"])
        scenario = payload["scenario"]
        candidate_id = payload["candidateId"]
        self.calls.append(candidate_id)
        if candidate_id in self.failure_ids:
            raise RuntimeError("synthetic backend failure; secret payload omitted")
        if candidate_id in self.invalid_json_ids:
            text = "not-json"
        else:
            output = self.overrides.get(candidate_id, expected_thesis(scenario))
            text = json.dumps(output, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        return {"text": text, "inputTokens": 256, "outputTokens": 128, "latencyMs": 12.5}

    def manifest(self):
        return {"provider": "LOCAL_FAKE", "model": "fake-gemma-3-27b-it", "revision": "local-fixture-v1"}


class FakeCriticBackend:
    def __init__(self, *, failure_ids: Iterable[str] = (), invalid_ids: Iterable[str] = ()):
        self.failure_ids = set(failure_ids)
        self.invalid_ids = set(invalid_ids)
        self.calls = []

    def review(self, messages, *, max_new_tokens: int):
        payload = json.loads(messages[-1]["content"])
        candidate_id = payload["candidateId"]
        self.calls.append(candidate_id)
        if candidate_id in self.failure_ids:
            raise RuntimeError("synthetic critic failure; secret payload omitted")
        if candidate_id in self.invalid_ids:
            text = "not-json"
        else:
            errors = sorted(set(payload.get("deterministicErrors", [])))
            verdict = "ACCEPT" if not errors else "REJECT"
            score = 2 if not errors else 0
            review = {
                "verdict": verdict,
                "scores": {"grounding": score, "classification": score, "riskCoverage": score, "decisionSupportSafety": score},
                "errorCodes": errors,
                "rationale": "Candidate satisfies deterministic checks." if not errors else "Candidate violates one or more deterministic checks.",
                "evidenceFields": [],
            }
            text = json.dumps(review, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
        return {"text": text, "inputTokens": 320, "outputTokens": 96, "latencyMs": 8.0}

    def manifest(self):
        return {"provider": "LOCAL_FAKE", "model": "fake-gemma-3-27b-it-critic", "revision": "local-fixture-v1"}
