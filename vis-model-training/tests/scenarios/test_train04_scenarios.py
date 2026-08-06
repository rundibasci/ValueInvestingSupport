import copy
import json
from collections import Counter
from pathlib import Path

from vis_training.scenarios.cli import main
from vis_training.scenarios.generator import SCENARIO_TYPES, generate_scenarios
from vis_training.scenarios.io import dataset_bytes, load_jsonl, load_object, sha256_bytes
from vis_training.scenarios.validator import validate_records

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config" / "scenarios-v1.json"
CATALOG_PATH = ROOT / "config" / "scenario-catalog-v1.json"
SCHEMA_PATH = ROOT / "schemas" / "thesis-input.schema.json"
DATASET_PATH = ROOT / "datasets" / "candidates" / "scenarios-v1.jsonl"
REPORT_PATH = ROOT / "reports" / "scenarios" / "distribution-v1.json"
PRIOR_PATHS = (ROOT / "datasets" / "seed-dataset-v1.jsonl", ROOT / "datasets" / "benchmark" / "base-benchmark-v1.jsonl")


def _inputs():
    return load_object(CONFIG_PATH), load_object(CATALOG_PATH), load_object(SCHEMA_PATH)


def _generated(seed=20260806):
    config, catalog, _ = _inputs()
    return generate_scenarios(config, catalog, seed=seed, count=500)


def test_canonical_dataset_matches_generator_and_report():
    records, report = _generated()
    assert DATASET_PATH.read_bytes() == dataset_bytes(records)
    assert load_object(REPORT_PATH) == report
    assert report["datasetSha256"] == sha256_bytes(DATASET_PATH.read_bytes())


def test_exact_distribution_and_all_catalog_variants_are_covered():
    records = load_jsonl(DATASET_PATH)
    difficulty = Counter(record["difficulty"] for record in records)
    categories = Counter(record["scenarioType"] for record in records)
    assert len(records) == 500
    assert difficulty == Counter({"ORDINARY": 300, "DIFFICULT": 125, "ADVERSARIAL_OR_INCOMPLETE": 75})
    assert set(categories) == set(SCENARIO_TYPES)
    assert min(categories.values()) >= 25
    catalog = load_object(CATALOG_PATH)
    observed = {(record["scenarioType"], record["variantId"]) for record in records}
    expected = {(category, variant) for category, value in catalog["categories"].items() for variant in value["variants"]}
    assert observed == expected


def test_canonical_dataset_passes_schema_semantics_and_contamination():
    _, catalog, schema = _inputs()
    failures, contamination = validate_records(
        load_jsonl(DATASET_PATH), input_schema=schema, catalog=catalog,
        expected_count=500, contamination_paths=PRIOR_PATHS,
    )
    assert failures == []
    assert contamination == []


def test_same_seed_is_byte_identical_and_different_seed_preserves_contract():
    first, first_report = _generated(20260806)
    second, second_report = _generated(20260806)
    different, different_report = _generated(20260807)
    assert dataset_bytes(first) == dataset_bytes(second)
    assert first_report == second_report
    assert dataset_bytes(first) != dataset_bytes(different)
    assert Counter(item["scenarioType"] for item in first) == Counter(item["scenarioType"] for item in different)
    assert Counter(item["difficulty"] for item in first) == Counter(item["difficulty"] for item in different)
    assert first_report["datasetSha256"] != different_report["datasetSha256"]


def test_records_are_unambiguously_synthetic_and_adversarial_text_is_isolated():
    records = load_jsonl(DATASET_PATH)
    assert all(record["input"]["symbol"].startswith("SYN") for record in records)
    assert all(record["input"]["companyName"].startswith("Synthetic Scenario Company ") for record in records)
    adversarial = [record for record in records if record["scenarioType"] == "ADVERSARIAL_INPUT"]
    assert len(adversarial) == 25
    assert all(record["input"]["dataQuality"] == "PARTIAL" for record in adversarial)
    assert all(all(warning.startswith("UNTRUSTED_TEXT_") for warning in record["input"]["deterministicWarnings"]) for record in adversarial)


def test_train03_failure_modes_have_explicit_scenario_coverage():
    catalog = load_object(CATALOG_PATH)
    coverage = catalog["train03FailureCoverage"]
    assert set(coverage) == {"valuation-bias", "overvaluation-direction", "payout-alarm", "unsupported-thresholds", "adversarial-review"}
    assert all(set(categories).issubset(SCENARIO_TYPES) for categories in coverage.values())
    records = load_jsonl(DATASET_PATH)
    dividend_risk = [item for item in records if item["scenarioType"] == "DIVIDEND_RISK"]
    assert all(item["input"]["payoutRatioPercent"] > 100 for item in dividend_risk)
    assert all(item["input"]["freeCashFlowTrend"] in {"DECLINING", "STRONGLY_DECLINING"} for item in dividend_risk)


def test_validator_rejects_financial_boundaries_and_unintentional_mismatch():
    records, _ = _generated()
    _, catalog, schema = _inputs()
    invalid = copy.deepcopy(records[:14])
    invalid[0]["input"]["marketPrice"] = 0
    invalid[1]["input"]["valueScore"] = 101
    invalid[2]["input"]["marginOfSafetyPercent"] += 1
    failures, _ = validate_records(invalid, input_schema=schema, catalog=catalog)
    assert any("exclusiveMinimum" in failure for failure in failures)
    assert any("maximum" in failure for failure in failures)
    assert any("margin of safety mismatch" in failure for failure in failures)


def test_intentional_margin_mismatch_is_accepted_only_for_declared_variant():
    records = load_jsonl(DATASET_PATH)
    _, catalog, schema = _inputs()
    intentional = next(item for item in records if item["variantId"] == "margin-of-safety-mismatch")
    failures, _ = validate_records([copy.deepcopy(intentional)], input_schema=schema, catalog=catalog)
    assert not any("margin of safety mismatch" in failure for failure in failures)
    invalid = copy.deepcopy(intentional)
    invalid["scenarioType"] = "UNDERVALUED_WEAK"
    invalid["variantId"] = "narrow-discount"
    failures, _ = validate_records([invalid], input_schema=schema, catalog=catalog)
    assert any("margin of safety mismatch" in failure for failure in failures)


def test_contamination_gate_detects_prior_symbol(tmp_path):
    records, _ = _generated()
    _, catalog, schema = _inputs()
    sample = copy.deepcopy(records[0])
    prior = tmp_path / "prior.jsonl"
    prior.write_text(json.dumps({
        "messages": [{"role": "user", "content": json.dumps(sample["input"])}],
        "metadata": {"exampleId": "OLD-1"},
    }) + "\n", encoding="utf-8")
    _, contamination = validate_records([sample], input_schema=schema, catalog=catalog, contamination_paths=(prior,))
    assert contamination == ["SCN-000001: identity collides with prior TRAIN dataset"]


def test_cli_generate_validate_and_reproducibility(tmp_path):
    output = tmp_path / "scenarios.jsonl"
    report = tmp_path / "distribution.json"
    shared = ["--config", str(CONFIG_PATH), "--catalog", str(CATALOG_PATH), "--input-schema", str(SCHEMA_PATH), "--seed-dataset", str(PRIOR_PATHS[0]), "--benchmark-dataset", str(PRIOR_PATHS[1])]
    assert main(["generate", *shared, "--seed", "20260806", "--count", "500", "--output", str(output), "--report", str(report)]) == 0
    assert output.read_bytes() == DATASET_PATH.read_bytes()
    assert main(["validate", *shared, "--dataset", str(output), "--expected-count", "500"]) == 0
    assert main(["verify-reproducibility", *shared, "--seed", "20260806", "--count", "500"]) == 0


def test_cli_distinguishes_configuration_and_validation_failures_and_preserves_output(tmp_path):
    output = tmp_path / "scenarios.jsonl"
    report = tmp_path / "distribution.json"
    output.write_text("preserve-me\n", encoding="utf-8")
    report.write_text("preserve-report\n", encoding="utf-8")
    assert main(["generate", "--config", str(tmp_path / "missing.json"), "--output", str(output), "--report", str(report)]) == 2
    assert output.read_text(encoding="utf-8") == "preserve-me\n"
    assert report.read_text(encoding="utf-8") == "preserve-report\n"

    invalid = tmp_path / "invalid.jsonl"
    invalid.write_text(DATASET_PATH.read_text(encoding="utf-8").replace('"marketPrice":', '"unexpected":1,"marketPrice":', 1), encoding="utf-8")
    assert main(["validate", "--catalog", str(CATALOG_PATH), "--input-schema", str(SCHEMA_PATH), "--seed-dataset", str(PRIOR_PATHS[0]), "--benchmark-dataset", str(PRIOR_PATHS[1]), "--dataset", str(invalid)]) == 3
