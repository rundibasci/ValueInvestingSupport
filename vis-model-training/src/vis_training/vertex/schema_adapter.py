"""Derive a Vertex AI-compatible `responseSchema` from a source JSON Schema.

Vertex AI's controlled generation accepts a subset of the OpenAPI 3.0 Schema
object: it does not support `$ref`/`$defs` (every reference must be inlined)
and it does not support a JSON Schema `type` array for nullable fields (it
uses a `nullable: true` boolean alongside a single `type` instead, and does
not support combining `nullable` with `anyOf`). Unrecognized JSON Schema
keywords (`$schema`, `$id`, `additionalProperties`) are dropped rather than
passed through unverified.

`thesis-output.schema.json` (and the broader thesis-schema family) remains
the unmodified source of truth per ADR-002's reuse decision
(`vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md`); this
module produces a derived artifact only, verified equivalent to the source
by `assert_schema_equivalent` below — it never edits the source file.
"""

from __future__ import annotations

from typing import Any, Dict, List

# JSON Schema keywords Vertex AI's responseSchema subset does not recognize;
# dropped rather than passed through, since passing an unsupported keyword to
# a live Vertex AI request is a request-time failure this module cannot
# observe from static analysis alone (see validation.md's Known Risks).
_UNSUPPORTED_KEYWORDS = {"$schema", "$id", "additionalProperties"}

# Keys copied through unchanged when present — every one of these is part of
# Vertex AI's documented responseSchema subset.
_PASSTHROUGH_KEYWORDS = (
    "type",
    "format",
    "description",
    "enum",
    "minLength",
    "maxLength",
    "minimum",
    "maximum",
    "exclusiveMinimum",
    "exclusiveMaximum",
    "minItems",
    "maxItems",
    "uniqueItems",
    "required",
)


def to_vertex_response_schema(source_schema: Dict[str, Any]) -> Dict[str, Any]:
    """Return a Vertex AI-compatible responseSchema derived from source_schema.

    `source_schema` is expected to be a JSON Schema document that may use
    `$ref`/`$defs` (resolved and inlined against the document's own
    top-level `$defs`) and `type` arrays for nullable fields (converted to
    `nullable: true`). The input is never mutated.
    """
    defs = source_schema.get("$defs", {})
    return _convert_node(source_schema, defs)


def _resolve_ref(ref: str, defs: Dict[str, Any]) -> Dict[str, Any]:
    prefix = "#/$defs/"
    if not ref.startswith(prefix):
        raise ValueError(f"Unsupported $ref target (only local $defs refs are supported): {ref}")
    name = ref[len(prefix):]
    if name not in defs:
        raise ValueError(f"$ref points at an undefined $defs entry: {ref}")
    return defs[name]


def _convert_node(node: Dict[str, Any], defs: Dict[str, Any]) -> Dict[str, Any]:
    if "$ref" in node:
        return _convert_node(_resolve_ref(node["$ref"], defs), defs)

    result: Dict[str, Any] = {}

    raw_type = node.get("type")
    if isinstance(raw_type, list):
        non_null_types = [t for t in raw_type if t != "null"]
        if len(non_null_types) != 1:
            raise ValueError(f"Cannot convert multi-type array to a single Vertex type: {raw_type}")
        result["type"] = non_null_types[0]
        if "null" in raw_type:
            result["nullable"] = True
    elif raw_type is not None:
        result["type"] = raw_type

    for key in _PASSTHROUGH_KEYWORDS:
        if key == "type":
            continue
        if key in node:
            result[key] = node[key]

    if "properties" in node:
        result["properties"] = {
            name: _convert_node(prop, defs) for name, prop in node["properties"].items()
        }

    if "items" in node:
        result["items"] = _convert_node(node["items"], defs)

    unsupported_present = _UNSUPPORTED_KEYWORDS & node.keys()
    # Intentionally silent: $schema/$id/additionalProperties are structural
    # JSON Schema metadata with no Vertex AI equivalent, not omissions a
    # caller needs to react to. Documented in this module's docstring.
    del unsupported_present

    return result


def assert_schema_equivalent(source_schema: Dict[str, Any], vertex_schema: Dict[str, Any]) -> None:
    """Raise AssertionError if vertex_schema is not structurally equivalent
    to source_schema (every property name, `required` entry, and `enum`
    value set in the source must have a corresponding entry in the derived
    schema). Resolves $ref in source_schema before comparing.
    """
    defs = source_schema.get("$defs", {})
    _assert_node_equivalent(source_schema, vertex_schema, defs, path="$")


def _assert_node_equivalent(
    source_node: Dict[str, Any],
    vertex_node: Dict[str, Any],
    defs: Dict[str, Any],
    *,
    path: str,
) -> None:
    if "$ref" in source_node:
        _assert_node_equivalent(_resolve_ref(source_node["$ref"], defs), vertex_node, defs, path=path)
        return

    source_type = source_node.get("type")
    expected_nullable = False
    if isinstance(source_type, list):
        non_null_types: List[Any] = [t for t in source_type if t != "null"]
        expected_type = non_null_types[0] if len(non_null_types) == 1 else None
        expected_nullable = "null" in source_type
    else:
        expected_type = source_type

    if expected_type is not None:
        assert vertex_node.get("type") == expected_type, (
            f"{path}: type mismatch (source={expected_type!r}, vertex={vertex_node.get('type')!r})"
        )
    assert bool(vertex_node.get("nullable", False)) == expected_nullable, (
        f"{path}: nullable mismatch (expected={expected_nullable}, vertex={vertex_node.get('nullable', False)})"
    )

    if "enum" in source_node:
        assert set(vertex_node.get("enum", [])) == set(source_node["enum"]), (
            f"{path}: enum mismatch"
        )

    if "required" in source_node:
        assert set(vertex_node.get("required", [])) == set(source_node["required"]), (
            f"{path}: required-field mismatch"
        )

    if "properties" in source_node:
        vertex_properties = vertex_node.get("properties", {})
        source_properties = source_node["properties"]
        assert set(vertex_properties.keys()) == set(source_properties.keys()), (
            f"{path}: property-name mismatch "
            f"(source={sorted(source_properties.keys())}, vertex={sorted(vertex_properties.keys())})"
        )
        for name, source_prop in source_properties.items():
            _assert_node_equivalent(
                source_prop, vertex_properties[name], defs, path=f"{path}.{name}"
            )

    if "items" in source_node:
        assert "items" in vertex_node, f"{path}: missing items"
        _assert_node_equivalent(source_node["items"], vertex_node["items"], defs, path=f"{path}[]")

    # Assert no $ref or type-array construct leaked through unconverted.
    assert "$ref" not in vertex_node, f"{path}: unresolved $ref present in derived schema"
    assert not isinstance(vertex_node.get("type"), list), f"{path}: type array present in derived schema"
