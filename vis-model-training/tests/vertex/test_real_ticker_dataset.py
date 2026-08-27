import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker

from vis_training.vertex.real_ticker_dataset import (
    CATEGORY,
    _COMPANIES,
    _VARIANTS,
    build_dataset,
)

ROOT = Path(__file__).resolve().parents[2]
INPUT_SCHEMA_PATH = ROOT / "schemas" / "thesis-input.schema.json"
OUTPUT_SCHEMA_PATH = ROOT / "schemas" / "thesis-output.schema.json"
CHECKED_IN_DATASET_PATH = ROOT / "datasets" / "benchmark" / "real-ticker-knowledge-leakage-v1.jsonl"


def test_produces_expected_case_count():
    records = build_dataset()
    assert len(records) == len(_COMPANIES) * len(_VARIANTS)


def test_every_example_id_is_unique():
    records = build_dataset()
    ids = [record["metadata"]["exampleId"] for record in records]
    assert len(ids) == len(set(ids))


def test_every_symbol_and_variant_combination_present():
    records = build_dataset()
    seen = {
        (record["metadata"]["realTicker"]["symbol"], record["metadata"]["realTicker"]["variantId"])
        for record in records
    }
    expected = {
        (company["symbol"], variant["variantId"])
        for company in _COMPANIES
        for variant in _VARIANTS
    }
    assert seen == expected


def test_every_record_is_category_knowledge_leakage():
    records = build_dataset()
    assert all(record["metadata"]["benchmarkCategory"] == CATEGORY for record in records)
    assert all(record["metadata"]["source"] == "REAL_TICKER_MANUAL_TEMPLATE" for record in records)


def test_every_record_has_a_real_company_name_and_symbol():
    records = build_dataset()
    real_symbols = {company["symbol"] for company in _COMPANIES}
    for record in records:
        input_data = json.loads(record["messages"][1]["content"])
        assert input_data["symbol"] in real_symbols
        assert input_data["companyName"]


def test_input_and_expected_validate_against_thesis_schemas():
    input_schema = json.loads(INPUT_SCHEMA_PATH.read_text(encoding="utf-8"))
    output_schema = json.loads(OUTPUT_SCHEMA_PATH.read_text(encoding="utf-8"))
    input_validator = Draft202012Validator(input_schema, format_checker=FormatChecker())
    output_validator = Draft202012Validator(output_schema, format_checker=FormatChecker())

    for record in build_dataset():
        input_data = json.loads(record["messages"][1]["content"])
        expected = json.loads(record["messages"][2]["content"])
        input_errors = list(input_validator.iter_errors(input_data))
        output_errors = list(output_validator.iter_errors(expected))
        assert not input_errors, f"{record['metadata']['exampleId']}: {input_errors}"
        assert not output_errors, f"{record['metadata']['exampleId']}: {output_errors}"


def test_strongly_declining_trend_always_has_a_bear_case_claim():
    # Mirrors system-prompt-v2.txt rule 10: a STRONGLY_DECLINING trend
    # requires at least one supported bear-case claim in `expected`.
    for record in build_dataset():
        input_data = json.loads(record["messages"][1]["content"])
        expected = json.loads(record["messages"][2]["content"])
        has_strongly_declining = any(
            input_data[field] == "STRONGLY_DECLINING"
            for field in ("revenueTrend", "earningsTrend", "freeCashFlowTrend")
        )
        if has_strongly_declining:
            assert expected["bearCase"], record["metadata"]["exampleId"]


def test_expected_never_mentions_the_real_fact_text():
    # The whole point of this dataset is that `expected` follows only the
    # supplied (fake) evidence — it must never restate the real-world fact
    # recorded in metadata for provenance.
    for record in build_dataset():
        expected_text = record["messages"][2]["content"]
        real_fact = record["metadata"]["realTicker"]["realFact"]
        # A crude but effective check: no long real-fact substring appears
        # verbatim in the expected thesis text.
        assert real_fact[:30] not in expected_text


def test_checked_in_dataset_matches_generator_output():
    generated = {r["metadata"]["exampleId"]: r for r in build_dataset()}
    checked_in = {}
    with open(CHECKED_IN_DATASET_PATH, encoding="utf-8") as handle:
        for line in handle:
            record = json.loads(line)
            checked_in[record["metadata"]["exampleId"]] = record
    assert checked_in == generated
