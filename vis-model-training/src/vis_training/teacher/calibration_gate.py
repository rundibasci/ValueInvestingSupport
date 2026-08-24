"""Deterministic go/no-go gate for TRAIN-05 calibration reports."""

from pathlib import Path
from typing import Any, Dict, List, Optional

from .errors import TeacherDataError
from .io import read_object, write_json


def _rate(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 6) if denominator else 0.0


def _criterion(name: str, actual: Any, operator: str, expected: Any) -> Dict[str, Any]:
    if operator == "eq":
        passed = actual == expected
    elif operator == "gte":
        passed = actual >= expected
    elif operator == "lte":
        passed = actual <= expected
    else:
        raise ValueError(f"Unsupported gate operator: {operator}")
    return {"name": name, "actual": actual, "operator": operator, "expected": expected, "passed": passed}


def _calibration_values(report: Dict[str, Any]) -> Dict[str, Any]:
    teacher = report.get("teacher")
    critic = report.get("critic")
    if not isinstance(teacher, dict) or not isinstance(critic, dict):
        denominators = report.get("denominators", {})
        teacher = {
            "scenarioCount": report.get("scenarioCount"),
            "categoryCount": len(report.get("byScenarioType", {})),
            "candidateSlots": denominators.get("candidateSlots"),
            "parseableCandidates": denominators.get("parseableCandidates"),
        }
        critic = {
            "reviewSlots": denominators.get("criticReviews"),
            "canonicalReviews": denominators.get("canonicalCriticReviews"),
            "usableReviews": denominators.get("usableCriticReviews"),
            "verdicts": report.get("critic", {}).get("verdicts", {}),
        }
    required = ("scenarioCount", "categoryCount", "candidateSlots", "parseableCandidates")
    if any(not isinstance(teacher.get(key), int) for key in required):
        raise TeacherDataError("Calibration report has incomplete teacher denominators")
    if any(not isinstance(critic.get(key), int) for key in ("reviewSlots", "canonicalReviews", "usableReviews")):
        raise TeacherDataError("Calibration report has incomplete critic denominators")
    verdicts = critic.get("verdicts", {})
    if not isinstance(verdicts, dict):
        raise TeacherDataError("Calibration report has invalid critic verdicts")
    return {"teacher": teacher, "critic": critic, "verdicts": verdicts}


def evaluate_calibration_gate(report: Dict[str, Any], human: Dict[str, Any], thresholds: Dict[str, Any]) -> Dict[str, Any]:
    if thresholds.get("formatVersion") != "1.0" or not thresholds.get("gateId"):
        raise TeacherDataError("Calibration gate configuration is invalid")
    values = _calibration_values(report)
    teacher, critic, verdicts = values["teacher"], values["critic"], values["verdicts"]
    review_count = human.get("reviewedCandidateCount")
    category_count = human.get("categoryCount")
    decisions = human.get("decisions", {})
    scores = human.get("scores", {})
    statuses = human.get("candidateStatusByDecision", {})
    if not isinstance(review_count, int) or not isinstance(category_count, int):
        raise TeacherDataError("Human-review summary has incomplete denominators")
    accepted = decisions.get("ACCEPT", 0)
    false_positives = statuses.get("SEMANTIC_REJECTED", {}).get("ACCEPT", 0)
    decisive = verdicts.get("ACCEPT", 0) + verdicts.get("REJECT", 0)
    criteria: List[Dict[str, Any]] = [
        _criterion("scenario_count", teacher["scenarioCount"], "gte", thresholds["minimumScenarioCount"]),
        _criterion("category_count", teacher["categoryCount"], "gte", thresholds["minimumCategoryCount"]),
        _criterion("candidate_slots", teacher["candidateSlots"], "eq", thresholds["expectedCandidateSlots"]),
        _criterion("parseable_rate", _rate(teacher["parseableCandidates"], teacher["candidateSlots"]), "gte", thresholds["minimumParseableRate"]),
        _criterion("usable_critic_rate", _rate(critic["usableReviews"], critic["reviewSlots"]), "gte", thresholds["minimumUsableCriticRate"]),
        _criterion("canonical_critic_rate", _rate(critic["canonicalReviews"], critic["reviewSlots"]), "gte", thresholds["minimumCanonicalCriticRate"]),
        _criterion("decisive_critic_rate", _rate(decisive, critic["usableReviews"]), "gte", thresholds["minimumDecisiveCriticRate"]),
        _criterion("human_review_count", review_count, "gte", thresholds["minimumHumanReviewCount"]),
        _criterion("human_category_count", category_count, "gte", thresholds["minimumHumanCategoryCount"]),
        _criterion("human_accept_rate", _rate(accepted, review_count), "gte", thresholds["minimumHumanAcceptRate"]),
        _criterion("validator_false_positive_rate", _rate(false_positives, review_count), "lte", thresholds["maximumValidatorFalsePositiveRate"]),
    ]
    for name, minimum in thresholds["minimumAverageScores"].items():
        actual = scores.get(name, {}).get("average")
        if not isinstance(actual, (int, float)):
            raise TeacherDataError(f"Human-review summary lacks average score: {name}")
        criteria.append(_criterion(f"average_{name}", actual, "gte", minimum))
    for name, maximum in thresholds["maximumZeroScoreRates"].items():
        distribution = scores.get(name, {}).get("distribution", {})
        zero_count = distribution.get("0", distribution.get(0, 0))
        criteria.append(_criterion(f"zero_score_rate_{name}", _rate(zero_count, review_count), "lte", maximum))
    if thresholds.get("requireResourceCleanup"):
        cleanup = report.get("resourceCleanup", {})
        criteria.extend([
            _criterion("pod_deleted", cleanup.get("podDeleted"), "eq", True),
            _criterion("network_volumes_removed", cleanup.get("networkVolumesFound"), "eq", 0),
        ])
    if thresholds.get("requireBulkNotStarted"):
        criteria.append(_criterion("bulk_not_started", report.get("bulkStarted"), "eq", False))
    if thresholds.get("requireAutomaticTrainingPromotionDisabled"):
        criteria.extend([
            _criterion("report_training_promotion_disabled", report.get("automaticTrainingPromotion"), "eq", False),
            _criterion("review_training_promotion_disabled", human.get("automaticTrainingPromotion"), "eq", False),
        ])
    failed = [item["name"] for item in criteria if not item["passed"]]
    return {
        "formatVersion": "1.0",
        "gateId": thresholds["gateId"],
        "decision": "GO" if not failed else "NO_GO",
        "bulkAuthorizedByGate": not failed,
        "criteria": criteria,
        "failedCriteria": failed,
    }


def evaluate_files(report_path: Path, human_path: Path, thresholds_path: Path, output_path: Optional[Path] = None) -> Dict[str, Any]:
    result = evaluate_calibration_gate(read_object(report_path), read_object(human_path), read_object(thresholds_path))
    if output_path is not None:
        write_json(output_path, result)
    return result
