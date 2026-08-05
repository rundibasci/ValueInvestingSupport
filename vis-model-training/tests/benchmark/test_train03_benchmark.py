import json
from collections import Counter
from pathlib import Path

import pytest

from vis_training.benchmark.catalog import CATEGORY_COUNTS, documents
from vis_training.benchmark.freeze import build_freeze_manifest, verify_freeze_manifest
from vis_training.benchmark.io import BenchmarkDataError, iter_jsonl
from vis_training.benchmark.metrics import compute_metrics
from vis_training.benchmark.runner import BenchmarkRunner, GenerationBackend
from vis_training.benchmark.review import prepare_review_form, validate_completed_review
from vis_training.validation.dataset_validator import validate_dataset

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / "datasets" / "benchmark" / "base-benchmark-v1.jsonl"
INPUT_SCHEMA = ROOT / "schemas" / "thesis-input.schema.json"
OUTPUT_SCHEMA = ROOT / "schemas" / "thesis-output.schema.json"


class ExpectedBackend(GenerationBackend):
    def __init__(self, *, invalid_ids=None, failure_ids=None):
        self.invalid_ids = set(invalid_ids or [])
        self.failure_ids = set(failure_ids or [])
        self.calls = []

    def generate(self, messages, *, max_new_tokens):
        user = json.loads(messages[1]["content"])
        example_id = user["symbol"]
        self.calls.append(example_id)
        if example_id in self.failure_ids:
            raise RuntimeError("synthetic failure with secret payload omitted")
        if example_id in self.invalid_ids:
            return {"text": "not json", "inputTokens": 10, "outputTokens": 2}
        return {
            "text": json.dumps({
                "classification": "UNDER_REVIEW",
                "confidence": 0.5,
                "summary": "Synthetic output.",
                "bullCase": [],
                "bearCase": [],
                "keyRisks": [],
                "keyAssumptions": [],
                "invalidationConditions": [],
                "dataWarnings": [],
                "humanReviewRequired": True,
            }),
            "inputTokens": 10,
            "outputTokens": 20,
        }

    def manifest(self):
        return {"backend": "expected-test-double"}


class GroundTruthBackend(GenerationBackend):
    def __init__(self, expected_by_symbol):
        self.expected_by_symbol = expected_by_symbol

    def generate(self, messages, *, max_new_tokens):
        symbol = json.loads(messages[1]["content"])["symbol"]
        return {"text": json.dumps(self.expected_by_symbol[symbol]), "inputTokens": 1, "outputTokens": 1}

    def manifest(self):
        return {"backend": "ground-truth-test-double"}


def _expected_by_symbol(path=DATASET):
    result = {}
    for document in iter_jsonl(path):
        symbol = json.loads(document["messages"][1]["content"])["symbol"]
        result[symbol] = json.loads(document["messages"][2]["content"])
    return result


def test_catalog_has_exact_category_distribution():
    prompt = (ROOT / "prompts" / "system-prompt-v2.txt").read_text(encoding="utf-8")
    generated = list(documents(prompt))
    counts = Counter(item["metadata"]["benchmarkCategory"] for item in generated)
    assert len(generated) == 50
    assert counts == Counter(CATEGORY_COUNTS)
    assert len({item["metadata"]["exampleId"] for item in generated}) == 50


def test_committed_benchmark_matches_catalog_and_is_valid():
    committed = list(iter_jsonl(DATASET))
    prompt = (ROOT / "prompts" / "system-prompt-v2.txt").read_text(encoding="utf-8")
    assert committed == list(documents(prompt))
    report = validate_dataset(DATASET, INPUT_SCHEMA, OUTPUT_SCHEMA)
    assert (report.records, report.valid, report.invalid, report.errors) == (50, 50, 0, 0)


def test_benchmark_does_not_overlap_seed_ids_or_symbols():
    seed = list(iter_jsonl(ROOT / "datasets" / "seed-dataset-v1.jsonl"))
    benchmark = list(iter_jsonl(DATASET))
    seed_ids = {item["metadata"]["exampleId"] for item in seed}
    benchmark_ids = {item["metadata"]["exampleId"] for item in benchmark}
    seed_symbols = {json.loads(item["messages"][1]["content"])["symbol"] for item in seed}
    benchmark_symbols = {json.loads(item["messages"][1]["content"])["symbol"] for item in benchmark}
    assert seed_ids.isdisjoint(benchmark_ids)
    assert seed_symbols.isdisjoint(benchmark_symbols)


def test_freeze_round_trip_and_tamper_detection(tmp_path):
    frozen = tmp_path / "input.txt"
    frozen.write_text("original\n", encoding="utf-8")
    manifest = tmp_path / "freeze.json"
    build_freeze_manifest([frozen], root=tmp_path, output=manifest)
    assert verify_freeze_manifest(manifest, root=tmp_path) == []
    frozen.write_text("changed\n", encoding="utf-8")
    assert verify_freeze_manifest(manifest, root=tmp_path) == ["hash mismatch: input.txt"]


def test_freeze_rejects_paths_outside_root(tmp_path):
    with pytest.raises(ValueError):
        build_freeze_manifest([ROOT / "pyproject.toml"], root=tmp_path, output=tmp_path / "freeze.json")


def test_runner_preserves_first_output_and_resumes(tmp_path):
    backend = ExpectedBackend()
    output = tmp_path / "results.jsonl"
    runner = BenchmarkRunner(backend)
    first = runner.run(DATASET, output, limit=2)
    original = output.read_bytes()
    second = runner.run(DATASET, output, limit=0)
    assert first == {"processed": 2, "skipped": 0, "totalSeen": 3}
    assert second["processed"] == 0 and second["skipped"] == 2
    assert output.read_bytes() == original
    assert len(list(iter_jsonl(output))) == 2


def test_runner_records_invalid_json_and_continues(tmp_path):
    backend = ExpectedBackend(invalid_ids={"B001"}, failure_ids={"B002"})
    output = tmp_path / "results.jsonl"
    summary = BenchmarkRunner(backend).run(DATASET, output, limit=3)
    records = list(iter_jsonl(output))
    assert summary["processed"] == 3
    assert records[0]["parseError"] == "INVALID_JSON"
    assert records[1]["generationError"] == "RuntimeError"
    assert records[2]["rawOutput"] is not None


def test_runner_rejects_duplicate_run_state(tmp_path):
    output = tmp_path / "results.jsonl"
    duplicate = {"exampleId": "same"}
    output.write_text(json.dumps(duplicate) + "\n" + json.dumps(duplicate) + "\n", encoding="utf-8")
    with pytest.raises(BenchmarkDataError):
        BenchmarkRunner(ExpectedBackend()).run(DATASET, output, limit=1)


def test_metrics_are_perfect_for_ground_truth_backend(tmp_path):
    output = tmp_path / "results.jsonl"
    BenchmarkRunner(GroundTruthBackend(_expected_by_symbol())).run(DATASET, output)
    report = compute_metrics(output, DATASET, OUTPUT_SCHEMA)
    global_metrics = report["global"]
    assert global_metrics["cases"] == 50
    for metric in (
        "jsonValidityRate",
        "schemaComplianceRate",
        "classificationAccuracy",
        "evidenceFieldPrecision",
        "humanReviewAccuracy",
        "exactFieldCoverageRate",
    ):
        assert global_metrics[metric] == 1.0
    assert global_metrics["unsupportedNumericClaimRate"] == 0.0
    assert global_metrics["prohibitedRecommendationRate"] == 0.0
    assert set(report["byCategory"]) == set(CATEGORY_COUNTS)


def test_metrics_count_non_json_as_failure(tmp_path):
    output = tmp_path / "results.jsonl"
    BenchmarkRunner(ExpectedBackend(invalid_ids={"B001"})).run(DATASET, output, limit=1)
    report = compute_metrics(output, DATASET, OUTPUT_SCHEMA)
    assert report["global"]["jsonValidityRate"] == 0.0
    assert report["global"]["classificationAccuracy"] == 0.0
    assert report["global"]["semanticAssessableCases"] == 0
    assert report["global"]["unsupportedNumericClaimRate"] is None
    assert report["global"]["prohibitedRecommendationRate"] is None
    assert report["global"]["averageLatencyMs"] is not None
    assert report["global"]["averageOutputLengthCharacters"] is not None


def test_metrics_recover_exact_markdown_wrapper_without_changing_canonical_rate(tmp_path):
    document = next(iter(iter_jsonl(DATASET)))
    expected = json.loads(document["messages"][2]["content"])
    record = {
        "exampleId": document["metadata"]["exampleId"],
        "category": document["metadata"]["benchmarkCategory"],
        "expected": expected,
        "rawOutput": "```json\n" + json.dumps(expected) + "\n```<end_of_turn>",
        "parsedOutput": None,
        "parseError": "INVALID_JSON",
        "generationError": None,
        "latencyMs": 1.0,
    }
    results = tmp_path / "results.jsonl"
    results.write_text(json.dumps(record) + "\n", encoding="utf-8")
    report = compute_metrics(results, DATASET, OUTPUT_SCHEMA)
    assert report["global"]["jsonValidityRate"] == 0.0
    assert report["recoverableDiagnostics"]["recoveredWrappedJson"] == 1
    assert report["recoverableDiagnostics"]["global"]["schemaComplianceRate"] == 1.0


def test_metrics_reject_duplicate_result_ids(tmp_path):
    document = next(iter(iter_jsonl(DATASET)))
    expected = json.loads(document["messages"][2]["content"])
    record = {"exampleId": document["metadata"]["exampleId"], "category": document["metadata"]["benchmarkCategory"], "expected": expected, "parsedOutput": expected}
    results = tmp_path / "results.jsonl"
    results.write_text(json.dumps(record) + "\n" + json.dumps(record) + "\n", encoding="utf-8")
    with pytest.raises(ValueError):
        compute_metrics(results, DATASET, OUTPUT_SCHEMA)


def test_review_selection_covers_all_categories_and_minimum(tmp_path):
    results = tmp_path / "results.jsonl"
    BenchmarkRunner(GroundTruthBackend(_expected_by_symbol())).run(DATASET, results)
    form = prepare_review_form(results, tmp_path / "review.json")
    assert len(form["reviews"]) == 20
    assert len({item["category"] for item in form["reviews"]}) == 9
    assert validate_completed_review(form)


def test_completed_review_validation(tmp_path):
    results = tmp_path / "results.jsonl"
    BenchmarkRunner(GroundTruthBackend(_expected_by_symbol())).run(DATASET, results)
    form = prepare_review_form(results, tmp_path / "review.json")
    for item in form["reviews"]:
        item["reviewerAlias"] = "reviewer-1"
        item["reviewDate"] = "2026-08-04"
        item["scores"] = {key: 2 for key in item["scores"]}
        item["accepted"] = True
    assert validate_completed_review(form) == []


def test_review_selection_caps_repeated_failure_mode_at_minimum(tmp_path):
    records = []
    for document in iter_jsonl(DATASET):
        records.append({
            "exampleId": document["metadata"]["exampleId"],
            "category": document["metadata"]["benchmarkCategory"],
            "parseError": "INVALID_JSON",
            "generationError": None,
        })
    results = tmp_path / "all-failed.jsonl"
    results.write_text("".join(json.dumps(item) + "\n" for item in records), encoding="utf-8")
    form = prepare_review_form(results, tmp_path / "review.json", minimum=20)
    assert len(form["reviews"]) == 20
    assert len({item["category"] for item in form["reviews"]}) == 9
