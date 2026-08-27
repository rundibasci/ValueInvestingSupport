import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator

from vis_training.vertex.schema_adapter import (
    assert_schema_equivalent,
    to_vertex_response_schema,
)

ROOT = Path(__file__).resolve().parents[2]
OUTPUT_SCHEMA_PATH = ROOT / "schemas" / "thesis-output.schema.json"
VERTEX_CONFIG_PATH = ROOT / "config" / "vertex-gemini-v1.json"


@pytest.fixture
def source_schema():
    schema = json.loads(OUTPUT_SCHEMA_PATH.read_text(encoding="utf-8"))
    Draft202012Validator.check_schema(schema)
    return schema


def test_real_output_schema_converts_without_error(source_schema):
    vertex_schema = to_vertex_response_schema(source_schema)
    assert isinstance(vertex_schema, dict)


def test_real_output_schema_is_equivalent(source_schema):
    vertex_schema = to_vertex_response_schema(source_schema)
    assert_schema_equivalent(source_schema, vertex_schema)


def test_ref_is_fully_inlined(source_schema):
    vertex_schema = to_vertex_response_schema(source_schema)
    dumped = json.dumps(vertex_schema)
    assert "$ref" not in dumped
    assert "$defs" not in vertex_schema


def test_evidence_array_items_are_inlined_objects(source_schema):
    vertex_schema = to_vertex_response_schema(source_schema)
    bull_case = vertex_schema["properties"]["bullCase"]
    assert bull_case["type"] == "array"
    evidence_item = bull_case["items"]
    assert evidence_item["type"] == "object"
    assert set(evidence_item["properties"].keys()) == {"claim", "evidenceFields"}
    assert evidence_item["required"] == source_schema["$defs"]["evidence"]["required"]


def test_no_type_array_survives_conversion(source_schema):
    vertex_schema = to_vertex_response_schema(source_schema)
    _assert_no_type_array(vertex_schema)


def _assert_no_type_array(node):
    if isinstance(node, dict):
        assert not isinstance(node.get("type"), list), f"type array present: {node.get('type')}"
        for value in node.values():
            _assert_no_type_array(value)
    elif isinstance(node, list):
        for item in node:
            _assert_no_type_array(item)


def test_nullable_input_field_converts_to_nullable_true():
    # thesis-input.schema.json uses type: [X, "null"] for optional numeric
    # fields (e.g. intrinsicValue) — exercise that pattern directly, since
    # thesis-output.schema.json itself has no nullable field to cover it.
    source = {
        "type": "object",
        "properties": {
            "intrinsicValue": {"type": ["number", "null"], "minimum": 0},
        },
        "required": ["intrinsicValue"],
    }
    vertex_schema = to_vertex_response_schema(source)
    field = vertex_schema["properties"]["intrinsicValue"]
    assert field["type"] == "number"
    assert field["nullable"] is True
    assert field["minimum"] == 0


def test_unsupported_keywords_are_dropped():
    source = {
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "$id": "https://example/schema.json",
        "type": "object",
        "additionalProperties": False,
        "properties": {"x": {"type": "string"}},
    }
    vertex_schema = to_vertex_response_schema(source)
    assert "$schema" not in vertex_schema
    assert "$id" not in vertex_schema
    assert "additionalProperties" not in vertex_schema


def test_equivalence_check_catches_dropped_property():
    source = {
        "type": "object",
        "properties": {"a": {"type": "string"}, "b": {"type": "string"}},
        "required": ["a", "b"],
    }
    broken_vertex_schema = {
        "type": "object",
        "properties": {"a": {"type": "string"}},
        "required": ["a"],
    }
    with pytest.raises(AssertionError):
        assert_schema_equivalent(source, broken_vertex_schema)


def test_equivalence_check_catches_leaked_ref():
    source = {"$defs": {"x": {"type": "string"}}, "$ref": "#/$defs/x"}
    broken_vertex_schema = {"$ref": "#/$defs/x"}
    with pytest.raises(AssertionError):
        assert_schema_equivalent(source, broken_vertex_schema)


def test_checked_in_config_response_schema_matches_live_adapter_output(source_schema):
    """config/vertex-gemini-v1.json must never drift from the derivation
    logic that produced it — if thesis-output.schema.json changes and the
    checked-in config isn't regenerated, this test fails instead of the
    config silently going stale."""
    config = json.loads(VERTEX_CONFIG_PATH.read_text(encoding="utf-8"))
    checked_in_response_schema = config["generationConfig"]["responseSchema"]
    live_response_schema = to_vertex_response_schema(source_schema)
    assert checked_in_response_schema == live_response_schema


def test_checked_in_config_pins_expected_values():
    config = json.loads(VERTEX_CONFIG_PATH.read_text(encoding="utf-8"))
    assert config["model"]["modelId"] == "gemini-2.5-flash"
    assert config["model"]["location"] == "europe-west1"
    assert config["generationConfig"]["temperature"] == 0.0
    assert config["generationConfig"]["responseMimeType"] == "application/json"
    assert config["groundingTools"] == []
    assert config["fewShotPolicy"]["enabled"] is False
    assert config["promptPath"] == "prompts/system-prompt-v2.txt"
