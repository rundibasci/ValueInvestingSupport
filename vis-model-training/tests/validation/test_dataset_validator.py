import copy
import json
from pathlib import Path

import pytest

from scripts import validate_dataset as cli
from vis_training.validation import codes
from vis_training.validation.dataset_validator import (
    DatasetConfigurationError,
    validate_dataset,
)

ROOT = Path(__file__).resolve().parents[2]
INPUT_SCHEMA = ROOT / "schemas" / "thesis-input.schema.json"
OUTPUT_SCHEMA = ROOT / "schemas" / "thesis-output.schema.json"
SEED_DATASET = ROOT / "datasets" / "seed-dataset-v1.jsonl"


@pytest.fixture
def valid_document():
    return json.loads((ROOT / "examples" / "example-001.json").read_text(encoding="utf-8"))


def embedded(document, index):
    return json.loads(document["messages"][index]["content"])


def replace_embedded(document, index, value):
    document["messages"][index]["content"] = json.dumps(value, separators=(",", ":"))


def write_dataset(tmp_path, documents):
    path = tmp_path / "dataset.jsonl"
    path.write_text("\n".join(json.dumps(item) for item in documents) + "\n", encoding="utf-8")
    return path


def validate(path):
    return validate_dataset(path, INPUT_SCHEMA, OUTPUT_SCHEMA)


def diagnostic_codes(report):
    return {item.code for item in report.diagnostics}


def test_seed_dataset_is_valid():
    report = validate(SEED_DATASET)
    assert (report.records, report.valid, report.invalid, report.errors) == (10, 10, 0, 0)


def test_alternative_valid_dataset_has_no_seed_count_dependency(tmp_path, valid_document):
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert (report.records, report.valid, report.invalid) == (1, 1, 0)


def test_missing_dataset_is_configuration_error(tmp_path):
    with pytest.raises(DatasetConfigurationError) as error:
        validate(tmp_path / "missing.jsonl")
    assert error.value.code == codes.DATASET_NOT_FOUND


def test_invalid_schema_is_configuration_error(tmp_path):
    schema = tmp_path / "invalid-schema.json"
    schema.write_text('{"type":7}', encoding="utf-8")
    with pytest.raises(DatasetConfigurationError) as error:
        validate_dataset(SEED_DATASET, schema, OUTPUT_SCHEMA)
    assert error.value.code == codes.SCHEMA_DEFINITION_INVALID


def test_malformed_jsonl_is_rejected_and_next_record_continues(tmp_path, valid_document):
    path = tmp_path / "dataset.jsonl"
    path.write_text("{invalid}\n" + json.dumps(valid_document) + "\n", encoding="utf-8")
    report = validate(path)
    assert (report.records, report.valid, report.invalid) == (2, 1, 1)
    assert codes.JSONL_PARSE_ERROR in diagnostic_codes(report)


def test_external_array_is_rejected(tmp_path, valid_document):
    path = tmp_path / "dataset.jsonl"
    path.write_text(json.dumps([valid_document]) + "\n", encoding="utf-8")
    report = validate(path)
    assert codes.JSONL_RECORD_NOT_OBJECT in diagnostic_codes(report)


def test_empty_dataset_is_rejected(tmp_path):
    path = tmp_path / "empty.jsonl"
    path.write_text("\n", encoding="utf-8")
    report = validate(path)
    assert report.errors == 1
    assert codes.EMPTY_DATASET in diagnostic_codes(report)


def test_invalid_conversation_roles_are_rejected(tmp_path, valid_document):
    valid_document["messages"][1]["role"] = "assistant"
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.CONVERSATION_STRUCTURE_INVALID in diagnostic_codes(report)


def test_malformed_embedded_json_is_rejected(tmp_path, valid_document):
    valid_document["messages"][2]["content"] = '{"classification":}'
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.EMBEDDED_JSON_INVALID in diagnostic_codes(report)


def test_text_outside_embedded_json_is_rejected(tmp_path, valid_document):
    valid_document["messages"][2]["content"] += " trailing"
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.TEXT_OUTSIDE_JSON in diagnostic_codes(report)


def test_input_schema_violation_is_rejected(tmp_path, valid_document):
    input_data = embedded(valid_document, 1)
    input_data["unexpected"] = True
    replace_embedded(valid_document, 1, input_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.INPUT_SCHEMA_INVALID in diagnostic_codes(report)


def test_output_schema_violation_is_rejected(tmp_path, valid_document):
    output_data = embedded(valid_document, 2)
    output_data["confidence"] = 2
    replace_embedded(valid_document, 2, output_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.OUTPUT_SCHEMA_INVALID in diagnostic_codes(report)


def test_impossible_date_is_rejected(tmp_path, valid_document):
    input_data = embedded(valid_document, 1)
    input_data["analysisDate"] = "2026-02-30"
    replace_embedded(valid_document, 1, input_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.INPUT_SCHEMA_INVALID in diagnostic_codes(report)


def test_missing_metadata_is_rejected(tmp_path, valid_document):
    del valid_document["metadata"]["exampleId"]
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.METADATA_INVALID in diagnostic_codes(report)


def test_duplicate_example_id_is_rejected(tmp_path, valid_document):
    duplicate = copy.deepcopy(valid_document)
    report = validate(write_dataset(tmp_path, [valid_document, duplicate]))
    assert (report.valid, report.invalid) == (1, 1)
    assert codes.DUPLICATE_EXAMPLE_ID in diagnostic_codes(report)


def test_repeated_symbol_and_scenario_are_allowed_with_unique_id(tmp_path, valid_document):
    second = copy.deepcopy(valid_document)
    second["metadata"]["exampleId"] = "VIS-TRAIN-ALT-0002"
    report = validate(write_dataset(tmp_path, [valid_document, second]))
    assert (report.valid, report.invalid, report.errors) == (2, 0, 0)


def test_missing_evidence_field_is_rejected(tmp_path, valid_document):
    output_data = embedded(valid_document, 2)
    output_data["bullCase"][0]["evidenceFields"][0] = "unknownField"
    replace_embedded(valid_document, 2, output_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.EVIDENCE_FIELD_MISSING in diagnostic_codes(report)


def test_null_evidence_field_is_rejected(tmp_path):
    document = json.loads((ROOT / "examples" / "example-003.json").read_text(encoding="utf-8"))
    output_data = embedded(document, 2)
    output_data["bullCase"] = [{"claim": "Intrinsic value is unavailable.", "evidenceFields": ["intrinsicValue"]}]
    replace_embedded(document, 2, output_data)
    report = validate(write_dataset(tmp_path, [document]))
    assert codes.EVIDENCE_FIELD_NULL in diagnostic_codes(report)


def test_insufficient_data_requires_classification_and_review(tmp_path):
    document = json.loads((ROOT / "examples" / "example-003.json").read_text(encoding="utf-8"))
    output_data = embedded(document, 2)
    output_data["classification"] = "UNDER_REVIEW"
    output_data["humanReviewRequired"] = False
    replace_embedded(document, 2, output_data)
    report = validate(write_dataset(tmp_path, [document]))
    assert {codes.INSUFFICIENT_CLASSIFICATION_REQUIRED, codes.HUMAN_REVIEW_REQUIRED} <= diagnostic_codes(report)


def test_stale_data_requires_review(tmp_path, valid_document):
    input_data = embedded(valid_document, 1)
    input_data["dataQuality"] = "STALE"
    replace_embedded(valid_document, 1, input_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.HUMAN_REVIEW_REQUIRED in diagnostic_codes(report)


def test_strong_decline_requires_bear_case(tmp_path, valid_document):
    input_data = embedded(valid_document, 1)
    input_data["freeCashFlowTrend"] = "STRONGLY_DECLINING"
    output_data = embedded(valid_document, 2)
    output_data["bearCase"] = []
    replace_embedded(valid_document, 1, input_data)
    replace_embedded(valid_document, 2, output_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert codes.BEAR_CASE_REQUIRED in diagnostic_codes(report)


@pytest.mark.parametrize(
    ("summary", "expected_code"),
    [
        ("The evidence says BUY.", codes.PROHIBITED_RECOMMENDATION),
        ("# Markdown heading", codes.MARKDOWN_PROHIBITED),
        ("The unsupported value is 999.", codes.UNSUPPORTED_NUMERIC_CLAIM),
    ],
)
def test_prohibited_output_content_is_rejected(tmp_path, valid_document, summary, expected_code):
    output_data = embedded(valid_document, 2)
    output_data["summary"] = summary
    replace_embedded(valid_document, 2, output_data)
    report = validate(write_dataset(tmp_path, [valid_document]))
    assert expected_code in diagnostic_codes(report)


def test_json_report_matches_text_counts(capsys):
    base_args = [
        "--dataset", str(SEED_DATASET),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
    ]
    assert cli.main(base_args + ["--format", "json"]) == 0
    json_report = json.loads(capsys.readouterr().out)
    assert (json_report["records"], json_report["valid"], json_report["invalid"]) == (10, 10, 0)
    assert cli.main(base_args) == 0
    text_report = capsys.readouterr().out
    assert "records: 10" in text_report and "invalid: 0" in text_report


def test_output_file_does_not_duplicate_stdout(tmp_path, capsys):
    output = tmp_path / "report.json"
    exit_code = cli.main([
        "--dataset", str(SEED_DATASET),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
        "--format", "json",
        "--output", str(output),
    ])
    assert exit_code == 0
    assert capsys.readouterr().out == ""
    assert json.loads(output.read_text(encoding="utf-8"))["records"] == 10


def test_output_write_failure_returns_two(tmp_path, capsys):
    output_directory = tmp_path / "directory"
    output_directory.mkdir()
    exit_code = cli.main([
        "--dataset", str(SEED_DATASET),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
        "--output", str(output_directory),
    ])
    assert exit_code == 2
    assert "OUTPUT_IO_ERROR" in capsys.readouterr().out


def test_cli_validation_failure_returns_one(tmp_path, valid_document, capsys):
    valid_document["messages"][2]["content"] += " trailing"
    path = write_dataset(tmp_path, [valid_document])
    exit_code = cli.main([
        "--dataset", str(path),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
    ])
    assert exit_code == 1
    assert "TEXT_OUTSIDE_JSON" in capsys.readouterr().out


def test_cli_diagnostic_does_not_echo_invalid_payload(tmp_path, valid_document, capsys):
    output_data = embedded(valid_document, 2)
    output_data["privateToken"] = "PRIVATE_TOKEN_ABC"
    replace_embedded(valid_document, 2, output_data)
    path = write_dataset(tmp_path, [valid_document])
    exit_code = cli.main([
        "--dataset", str(path),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
    ])
    output = capsys.readouterr().out
    assert exit_code == 1
    assert "OUTPUT_SCHEMA_INVALID" in output
    assert "PRIVATE_TOKEN_ABC" not in output


def test_cli_configuration_failure_returns_two(tmp_path, capsys):
    exit_code = cli.main([
        "--dataset", str(tmp_path / "missing.jsonl"),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
        "--format", "json",
    ])
    assert exit_code == 2
    report = json.loads(capsys.readouterr().out)
    assert report["diagnostics"][0]["code"] == codes.DATASET_NOT_FOUND


def test_cli_internal_failure_returns_three(monkeypatch, capsys):
    monkeypatch.setattr(cli, "validate_dataset", lambda *args: (_ for _ in ()).throw(RuntimeError("secret payload")))
    exit_code = cli.main([
        "--dataset", str(SEED_DATASET),
        "--input-schema", str(INPUT_SCHEMA),
        "--output-schema", str(OUTPUT_SCHEMA),
    ])
    assert exit_code == 3
    output = capsys.readouterr().out
    assert "INTERNAL_ERROR" in output
    assert "secret payload" not in output
