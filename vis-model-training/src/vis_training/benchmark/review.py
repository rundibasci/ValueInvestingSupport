"""Deterministic manual-review selection and empty review form creation."""

from collections import defaultdict
from datetime import date
from pathlib import Path
from typing import Any, Dict, Iterable

from .io import iter_jsonl, write_json

REVIEW_FORMAT_VERSION = "1.0"
DIMENSIONS = (
    "summaryCorrectness",
    "bullBearBalance",
    "riskQuality",
    "inputAdherence",
    "reviewerUtility",
)
# Dimensions required in addition to DIMENSIONS, keyed by category — used for
# rubric criteria that only make sense for one specific category (TA3's
# knowledgeLeakage applies only to REAL_TICKER_KNOWLEDGE_LEAKAGE cases; every
# other category is scored on DIMENSIONS alone). See rubrics/manual-review-v1.json.
CONDITIONAL_DIMENSIONS: Dict[str, tuple] = {
    "REAL_TICKER_KNOWLEDGE_LEAKAGE": ("knowledgeLeakage",),
}


def _required_dimensions(category: Any) -> tuple:
    return DIMENSIONS + CONDITIONAL_DIMENSIONS.get(category, ())


def select_review_ids(results: Iterable[Dict[str, Any]], *, minimum: int = 20) -> list:
    records = list(results)
    by_category = defaultdict(list)
    for item in records:
        by_category[item.get("category") or "UNSPECIFIED"].append(item)
    selected = []
    for category in sorted(by_category):
        selected.extend(
            item["exampleId"]
            for item in sorted(by_category[category], key=lambda value: value["exampleId"])[:2]
        )
    failures = [
        item["exampleId"]
        for item in sorted(records, key=lambda value: value["exampleId"])
        if item.get("parseError") or item.get("generationError")
    ]
    for example_id in failures:
        if len(selected) >= minimum:
            break
        if example_id not in selected:
            selected.append(example_id)
    for item in sorted(records, key=lambda value: value["exampleId"]):
        if len(selected) >= minimum:
            break
        if item["exampleId"] not in selected:
            selected.append(item["exampleId"])
    return selected


def prepare_review_form(results_path: Path, output_path: Path, *, minimum: int = 20) -> Dict[str, Any]:
    records = list(iter_jsonl(results_path))
    by_id = {item["exampleId"]: item for item in records}
    selected = select_review_ids(records, minimum=minimum)
    form = {
        "formatVersion": REVIEW_FORMAT_VERSION,
        "selectionMethod": "two per category, then parse/generation failures, then exampleId order",
        "minimumCases": minimum,
        "reviews": [
            {
                "exampleId": example_id,
                "category": by_id[example_id].get("category"),
                "reviewerAlias": None,
                "reviewDate": None,
                "scores": {
                    dimension: None
                    for dimension in _required_dimensions(by_id[example_id].get("category"))
                },
                "notes": "",
                "accepted": None,
            }
            for example_id in selected
        ],
    }
    write_json(output_path, form)
    return form


def validate_completed_review(form: Dict[str, Any], *, minimum_category_count: int = 9) -> list:
    """minimum_category_count defaults to 9 to match TRAIN-03's existing
    9-category base-benchmark-v1 dataset unchanged. A review pass that also
    covers TA3's TRAIN-04 scenario catalog and/or the new
    REAL_TICKER_KNOWLEDGE_LEAKAGE set must pass the actual combined category
    count for that specific run — this default is not a claim about every
    possible dataset's category count."""
    failures = []
    reviews = form.get("reviews")
    if not isinstance(reviews, list) or len(reviews) < form.get("minimumCases", 20):
        return ["review sample is smaller than the declared minimum"]
    categories = set()
    for review in reviews:
        example_id = review.get("exampleId", "unknown")
        category = review.get("category")
        categories.add(category)
        if not review.get("reviewerAlias") or not review.get("reviewDate"):
            failures.append(f"incomplete reviewer metadata: {example_id}")
        scores = review.get("scores", {})
        if any(scores.get(dimension) not in {0, 1, 2} for dimension in _required_dimensions(category)):
            failures.append(f"incomplete scores: {example_id}")
        if not isinstance(review.get("accepted"), bool):
            failures.append(f"missing acceptance: {example_id}")
    if None in categories or len(categories) < minimum_category_count:
        failures.append("manual review does not cover all benchmark categories")
    return failures
