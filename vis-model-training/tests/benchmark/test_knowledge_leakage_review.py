"""TA3: knowledgeLeakage is a conditional review dimension, required only
for REAL_TICKER_KNOWLEDGE_LEAKAGE cases, added on top of TRAIN-03's
existing universal DIMENSIONS without changing their default behavior."""

from vis_training.benchmark.review import (
    DIMENSIONS,
    prepare_review_form,
    validate_completed_review,
)


def _result(example_id, category):
    return {
        "exampleId": example_id,
        "category": category,
        "parseError": None,
        "generationError": None,
    }


def test_prepare_review_form_requires_knowledge_leakage_only_for_that_category(tmp_path):
    results_path = tmp_path / "results.jsonl"
    results_path.write_text(
        '{"exampleId": "RT-001", "category": "REAL_TICKER_KNOWLEDGE_LEAKAGE", '
        '"parseError": null, "generationError": null}\n'
        '{"exampleId": "SYN-001", "category": "UNDERVALUED_STRONG", '
        '"parseError": null, "generationError": null}\n',
        encoding="utf-8",
    )
    form = prepare_review_form(results_path, tmp_path / "form.json", minimum=2)
    by_id = {review["exampleId"]: review for review in form["reviews"]}

    assert "knowledgeLeakage" in by_id["RT-001"]["scores"]
    assert "knowledgeLeakage" not in by_id["SYN-001"]["scores"]
    for dimension in DIMENSIONS:
        assert dimension in by_id["RT-001"]["scores"]
        assert dimension in by_id["SYN-001"]["scores"]


def _completed_review(*, category, include_knowledge_leakage_score):
    scores = {dimension: 2 for dimension in DIMENSIONS}
    if include_knowledge_leakage_score:
        scores["knowledgeLeakage"] = 2
    return {
        "exampleId": "X-001",
        "category": category,
        "reviewerAlias": "reviewer",
        "reviewDate": "2026-08-27",
        "scores": scores,
        "notes": "",
        "accepted": True,
    }


def test_validate_completed_review_requires_knowledge_leakage_score_for_that_category():
    form = {
        "minimumCases": 1,
        "reviews": [_completed_review(category="REAL_TICKER_KNOWLEDGE_LEAKAGE", include_knowledge_leakage_score=False)],
    }
    failures = validate_completed_review(form, minimum_category_count=1)
    assert any("incomplete scores" in failure for failure in failures)


def test_validate_completed_review_passes_with_knowledge_leakage_score():
    form = {
        "minimumCases": 1,
        "reviews": [_completed_review(category="REAL_TICKER_KNOWLEDGE_LEAKAGE", include_knowledge_leakage_score=True)],
    }
    failures = validate_completed_review(form, minimum_category_count=1)
    assert failures == []


def test_validate_completed_review_does_not_require_knowledge_leakage_for_other_categories():
    form = {
        "minimumCases": 1,
        "reviews": [_completed_review(category="UNDERVALUED_STRONG", include_knowledge_leakage_score=False)],
    }
    failures = validate_completed_review(form, minimum_category_count=1)
    assert failures == []


def test_default_minimum_category_count_still_nine_for_backward_compatibility():
    # TRAIN-03's existing base-benchmark-v1 dataset has exactly 9 categories;
    # this default must not change just because TA3 added a 10th category type.
    reviews = [
        _completed_review(category=f"CAT_{i}", include_knowledge_leakage_score=False)
        for i in range(8)
    ]
    form = {"minimumCases": 1, "reviews": reviews}
    failures = validate_completed_review(form)
    assert "manual review does not cover all benchmark categories" in failures
