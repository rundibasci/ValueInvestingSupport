"""Deterministic human-review sampling and completion gate."""

from collections import Counter, defaultdict
from pathlib import Path
from statistics import mean
from typing import Any, Dict, List

from .errors import TeacherDataError
from .io import iter_jsonl, read_object, sha256_file, write_json


def prepare_review(candidates_path: Path, output_path: Path, minimum: int = 30, minimum_categories: int = 14) -> Dict[str, Any]:
    candidates = list(iter_jsonl(candidates_path))
    groups = defaultdict(list)
    for item in candidates:
        groups[item["scenarioType"]].append(item)
    selected = []
    for category in sorted(groups):
        selected.append(sorted(groups[category], key=lambda x: x["candidateId"])[0])
    chosen = {item["candidateId"] for item in selected}
    for item in sorted(candidates, key=lambda x: (x["status"] == "CRITIC_PENDING", x["candidateId"])):
        if len(selected) >= minimum: break
        if item["candidateId"] not in chosen:
            selected.append(item); chosen.add(item["candidateId"])
    if len(selected) < minimum:
        raise TeacherDataError(f"Only {len(selected)} candidates available; {minimum} required")
    if minimum_categories <= 0 or minimum_categories > len(groups):
        raise TeacherDataError(f"Review requires {minimum_categories} categories but only {len(groups)} are available")
    form = {"formatVersion": "1.0", "minimumReviews": minimum, "minimumCategories": minimum_categories, "automaticTrainingPromotion": False,
            "reviews": [{"candidateId": x["candidateId"], "scenarioType": x["scenarioType"], "candidateStatus": x["status"],
                         "reviewerAlias": None, "reviewedAt": None, "accepted": None,
                         "scores": {"grounding": None, "classification": None, "riskCoverage": None, "decisionSupportSafety": None}, "notes": None}
                        for x in selected]}
    write_json(output_path, form)
    return form


def check_review(path: Path) -> Dict[str, Any]:
    form = read_object(path); reviews = form.get("reviews", []); minimum = form.get("minimumReviews", 30); minimum_categories = form.get("minimumCategories", 14)
    incomplete = []
    for review in reviews:
        scores = review.get("scores", {})
        if not review.get("reviewerAlias") or not review.get("reviewedAt") or not isinstance(review.get("accepted"), bool) or any(scores.get(k) not in (0, 1, 2) for k in ("grounding", "classification", "riskCoverage", "decisionSupportSafety")):
            incomplete.append(review.get("candidateId"))
    categories = {x.get("scenarioType") for x in reviews}
    return {"complete": len(reviews) >= minimum and not incomplete and len(categories) >= minimum_categories, "reviewCount": len(reviews), "categoryCount": len(categories), "incompleteCandidateIds": incomplete}


def summarize_review(path: Path, output_path: Path) -> Dict[str, Any]:
    completion = check_review(path)
    if not completion["complete"]:
        raise TeacherDataError("Human review is incomplete and cannot be summarized")
    form = read_object(path)
    reviews = form["reviews"]
    decisions = Counter("ACCEPT" if item["accepted"] else "REJECT" for item in reviews)
    status_decisions = defaultdict(Counter)
    for item in reviews:
        status_decisions[item["candidateStatus"]]["ACCEPT" if item["accepted"] else "REJECT"] += 1
    score_summary = {}
    for name in ("grounding", "classification", "riskCoverage", "decisionSupportSafety"):
        values = [item["scores"][name] for item in reviews]
        score_summary[name] = {
            "average": round(mean(values), 3),
            "distribution": {str(score): values.count(score) for score in (0, 1, 2)},
        }
    summary = {
        "schemaVersion": 1,
        "phase": "TRAIN-05",
        "reviewGate": "calibration-human-review",
        "status": "COMPLETE",
        "automaticTrainingPromotion": False,
        "reviewedCandidateCount": len(reviews),
        "categoryCount": completion["categoryCount"],
        "decisions": {name: decisions.get(name, 0) for name in ("ACCEPT", "REJECT")},
        "candidateStatusByDecision": {
            status: {name: counts.get(name, 0) for name in ("ACCEPT", "REJECT")}
            for status, counts in sorted(status_decisions.items())
        },
        "scores": score_summary,
        "sourceArtifacts": {"humanReviewSha256": sha256_file(path)},
        "sanitization": {"containsRawModelOutput": False, "containsSecrets": False, "containsPaymentData": False},
    }
    write_json(output_path, summary)
    return summary
