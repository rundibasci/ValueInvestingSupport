"""Deterministic human-review sampling and completion gate."""

from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List

from .errors import TeacherDataError
from .io import iter_jsonl, write_json


def prepare_review(candidates_path: Path, output_path: Path, minimum: int = 30) -> Dict[str, Any]:
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
    form = {"formatVersion": "1.0", "minimumReviews": minimum, "automaticTrainingPromotion": False,
            "reviews": [{"candidateId": x["candidateId"], "scenarioType": x["scenarioType"], "candidateStatus": x["status"],
                         "reviewerAlias": None, "reviewedAt": None, "accepted": None,
                         "scores": {"grounding": None, "classification": None, "riskCoverage": None, "decisionSupportSafety": None}, "notes": None}
                        for x in selected]}
    write_json(output_path, form)
    return form


def check_review(path: Path) -> Dict[str, Any]:
    from .io import read_object
    form = read_object(path); reviews = form.get("reviews", []); minimum = form.get("minimumReviews", 30)
    incomplete = []
    for review in reviews:
        scores = review.get("scores", {})
        if not review.get("reviewerAlias") or not review.get("reviewedAt") or not isinstance(review.get("accepted"), bool) or any(scores.get(k) not in (0, 1, 2) for k in ("grounding", "classification", "riskCoverage", "decisionSupportSafety")):
            incomplete.append(review.get("candidateId"))
    categories = {x.get("scenarioType") for x in reviews}
    return {"complete": len(reviews) >= minimum and not incomplete and len(categories) >= 14, "reviewCount": len(reviews), "categoryCount": len(categories), "incompleteCandidateIds": incomplete}
