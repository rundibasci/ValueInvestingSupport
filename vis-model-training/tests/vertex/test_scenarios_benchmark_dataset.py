"""TA3: scenarios-v1.jsonl -> benchmark-harness contract converter tests."""

import json
from pathlib import Path

import pytest

from vis_training.validation.semantic_validator import validate_semantics
from vis_training.vertex.scenarios_benchmark_dataset import build_dataset, convert_record

ROOT = Path(__file__).resolve().parents[2]
SCENARIOS_PATH = ROOT / "datasets" / "candidates" / "scenarios-v1.jsonl"
OUTPUT_PATH = ROOT / "datasets" / "benchmark" / "scenarios-benchmark-v1.jsonl"


def _source_scenarios():
    with open(SCENARIOS_PATH, encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


@pytest.fixture(scope="module")
def source_scenarios():
    return _source_scenarios()


@pytest.fixture(scope="module")
def converted_records(source_scenarios):
    return build_dataset(SCENARIOS_PATH)


def test_record_count_matches_source(source_scenarios, converted_records):
    assert len(converted_records) == len(source_scenarios)


def test_every_example_id_unique_and_matches_scenario_id(source_scenarios, converted_records):
    ids = [record["metadata"]["exampleId"] for record in converted_records]
    assert len(set(ids)) == len(ids)
    assert ids == [scenario["scenarioId"] for scenario in source_scenarios]


def test_metadata_has_fields_dataset_validator_requires(converted_records):
    for record in converted_records:
        metadata = record["metadata"]
        for field in ("exampleId", "scenarioType", "source", "datasetVersion"):
            assert metadata.get(field), f"missing {field} in {metadata}"


def test_every_record_passes_semantic_validator(source_scenarios, converted_records):
    # This is the exact rule set scripts/validate_dataset.py enforces
    # (src/vis_training/validation/semantic_validator.py) — run it directly
    # here so a regression in derive_expected_thesis fails fast in the unit
    # suite instead of only being caught by a separate CLI invocation.
    for scenario, record in zip(source_scenarios, converted_records):
        input_data = json.loads(record["messages"][1]["content"])
        output_data = json.loads(record["messages"][2]["content"])
        diagnostics = validate_semantics(
            input_data, output_data, line=0, example_id=scenario["scenarioId"]
        )
        assert diagnostics == [], f"{scenario['scenarioId']}: {diagnostics}"


def test_insufficient_data_quality_forces_insufficient_data_classification(converted_records):
    insufficient = [
        record
        for record in converted_records
        if json.loads(record["messages"][1]["content"]).get("dataQuality") == "INSUFFICIENT"
    ]
    assert insufficient, "fixture expects at least one INSUFFICIENT-quality scenario"
    for record in insufficient:
        output_data = json.loads(record["messages"][2]["content"])
        assert output_data["classification"] == "INSUFFICIENT_DATA"
        assert output_data["humanReviewRequired"] is True


def test_review_data_qualities_force_human_review_required(converted_records):
    reviewable = [
        record
        for record in converted_records
        if json.loads(record["messages"][1]["content"]).get("dataQuality")
        in {"INSUFFICIENT", "INCONSISTENT", "STALE"}
    ]
    assert reviewable, "fixture expects at least one reviewable-quality scenario"
    for record in reviewable:
        output_data = json.loads(record["messages"][2]["content"])
        assert output_data["humanReviewRequired"] is True


def test_convert_record_never_cites_a_null_evidence_field():
    scenario = {
        "scenarioId": "SCN-TEST-NULL",
        "scenarioType": "TEST_TYPE",
        "input": {
            "symbol": "TEST",
            "companyName": "Test Co",
            "analysisDate": "2026-08-28",
            "marketPrice": None,
            "intrinsicValue": None,
            "marginOfSafetyPercent": None,
            "valueScore": 50.0,
            "dividendYieldPercent": None,
            "payoutRatioPercent": None,
            "netDebtToEbitda": None,
            "revenueTrend": "STABLE",
            "earningsTrend": "STABLE",
            "freeCashFlowTrend": "STABLE",
            "dataQuality": "PARTIAL",
            "deterministicWarnings": [],
        },
    }
    record = convert_record(scenario, system_prompt="SYSTEM")
    output_data = json.loads(record["messages"][2]["content"])
    diagnostics = validate_semantics(
        scenario["input"], output_data, line=0, example_id=scenario["scenarioId"]
    )
    assert diagnostics == []


def test_checked_in_dataset_matches_live_converter_output(source_scenarios, converted_records):
    checked_in = [json.loads(line) for line in OUTPUT_PATH.read_text(encoding="utf-8").splitlines() if line]
    assert checked_in == converted_records
