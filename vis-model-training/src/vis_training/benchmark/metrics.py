"""Predeclared TRAIN-03 automatic metric definitions and aggregation."""

import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from statistics import mean
from typing import Any, Dict, Iterable, Optional

from jsonschema import Draft202012Validator, FormatChecker

from vis_training.validation import codes
from vis_training.validation.semantic_validator import validate_semantics

from .io import iter_jsonl, read_json, write_json

METRICS_FORMAT_VERSION = "1.0"
_WRAPPED_JSON = re.compile(
    r"^\s*```(?:json)?\s*(\{.*\})\s*```(?:<end_of_turn>)?\s*$",
    re.DOTALL | re.IGNORECASE,
)


def _recover_wrapped_json(raw_output: Any) -> Optional[Dict[str, Any]]:
    """Recover only the exact Markdown/end-token wrapper seen in smoke output.

    This is diagnostic and never changes canonical JSON validity or stored output.
    """
    if not isinstance(raw_output, str):
        return None
    match = _WRAPPED_JSON.fullmatch(raw_output)
    if not match:
        return None
    try:
        candidate = json.loads(match.group(1))
    except json.JSONDecodeError:
        return None
    return candidate if isinstance(candidate, dict) else None


def _evidence_pairs(output: Dict[str, Any]) -> set:
    pairs = set()
    for section in ("bullCase", "bearCase"):
        for index, item in enumerate(output.get(section, [])):
            if not isinstance(item, dict):
                continue
            for field in item.get("evidenceFields", []):
                if isinstance(field, str):
                    pairs.add((section, index, field))
    return pairs


def _field_coverage(output: Dict[str, Any]) -> set:
    return set(output.keys())


def _safe_rate(numerator: int, denominator: int) -> Optional[float]:
    return round(numerator / denominator, 6) if denominator else None


def _aggregate(records: Iterable[Dict[str, Any]], output_validator: Any) -> Dict[str, Any]:
    items = list(records)
    total = len(items)
    valid_json = schema_valid = classification_correct = human_review_correct = 0
    evidence_correct = evidence_total = unsupported_numeric = prohibited = 0
    exact_coverage = 0
    latencies = []
    output_lengths = []
    failed_ids = defaultdict(list)
    for item in items:
        if isinstance(item.get("latencyMs"), (int, float)):
            latencies.append(item["latencyMs"])
        raw_output = item.get("rawOutput")
        if isinstance(raw_output, str):
            output_lengths.append(len(raw_output))
        parsed = item.get("parsedOutput")
        expected = item.get("expected")
        example_id = item.get("exampleId")
        if not isinstance(parsed, dict) or not isinstance(expected, dict):
            failed_ids["jsonValidity"].append(example_id)
            continue
        valid_json += 1
        schema_errors = list(output_validator.iter_errors(parsed))
        if not schema_errors:
            schema_valid += 1
        else:
            failed_ids["schemaCompliance"].append(example_id)
        if parsed.get("classification") == expected.get("classification"):
            classification_correct += 1
        else:
            failed_ids["classificationAccuracy"].append(example_id)
        if parsed.get("humanReviewRequired") == expected.get("humanReviewRequired"):
            human_review_correct += 1
        else:
            failed_ids["humanReviewAccuracy"].append(example_id)

        expected_evidence = _evidence_pairs(expected)
        actual_evidence = _evidence_pairs(parsed)
        evidence_total += len(actual_evidence)
        evidence_correct += len(actual_evidence & expected_evidence)
        if _field_coverage(parsed) == _field_coverage(expected):
            exact_coverage += 1
        else:
            failed_ids["exactFieldCoverage"].append(example_id)

        input_data = item.get("input")
        if isinstance(input_data, dict):
            diagnostics = validate_semantics(
                input_data, parsed, line=1, example_id=example_id
            )
            diagnostic_codes = {diagnostic.code for diagnostic in diagnostics}
            if codes.UNSUPPORTED_NUMERIC_CLAIM in diagnostic_codes:
                unsupported_numeric += 1
                failed_ids["unsupportedNumericClaim"].append(example_id)
            if codes.PROHIBITED_RECOMMENDATION in diagnostic_codes:
                prohibited += 1
                failed_ids["prohibitedRecommendation"].append(example_id)
    return {
        "cases": total,
        "semanticAssessableCases": valid_json,
        "jsonValidityRate": _safe_rate(valid_json, total),
        "schemaComplianceRate": _safe_rate(schema_valid, total),
        "classificationAccuracy": _safe_rate(classification_correct, total),
        "evidenceFieldPrecision": _safe_rate(evidence_correct, evidence_total),
        "unsupportedNumericClaimRate": _safe_rate(unsupported_numeric, valid_json),
        "prohibitedRecommendationRate": _safe_rate(prohibited, valid_json),
        "humanReviewAccuracy": _safe_rate(human_review_correct, total),
        "exactFieldCoverageRate": _safe_rate(exact_coverage, total),
        "averageOutputLengthCharacters": round(mean(output_lengths), 3) if output_lengths else None,
        "averageLatencyMs": round(mean(latencies), 3) if latencies else None,
        "failedExampleIds": {key: value for key, value in sorted(failed_ids.items())},
    }


def _dataset_inputs(dataset_path: Path) -> Dict[str, Dict[str, Any]]:
    inputs = {}
    for document in iter_jsonl(dataset_path):
        metadata = document.get("metadata", {})
        messages = document.get("messages", [])
        example_id = metadata.get("exampleId")
        if not isinstance(example_id, str) or len(messages) != 3:
            raise ValueError("Invalid benchmark record")
        inputs[example_id] = json.loads(messages[1]["content"])
    return inputs


def compute_metrics(
    results_path: Path,
    dataset_path: Path,
    output_schema_path: Path,
    *,
    output_path: Optional[Path] = None,
) -> Dict[str, Any]:
    schema = read_json(output_schema_path)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    inputs = _dataset_inputs(dataset_path)
    records = []
    categories = defaultdict(list)
    seen = set()
    for result in iter_jsonl(results_path):
        example_id = result.get("exampleId")
        if example_id not in inputs or example_id in seen:
            raise ValueError("Results contain an unknown or duplicate exampleId")
        seen.add(example_id)
        enriched = dict(result)
        enriched["input"] = inputs[example_id]
        records.append(enriched)
        categories[result.get("category") or "UNSPECIFIED"].append(enriched)
    category_counts = Counter(item.get("category") or "UNSPECIFIED" for item in records)
    recoverable_records = []
    recovered_count = 0
    for item in records:
        diagnostic = dict(item)
        if not isinstance(diagnostic.get("parsedOutput"), dict):
            recovered = _recover_wrapped_json(diagnostic.get("rawOutput"))
            if recovered is not None:
                diagnostic["parsedOutput"] = recovered
                recovered_count += 1
        recoverable_records.append(diagnostic)
    recoverable_by_category = defaultdict(list)
    for item in recoverable_records:
        recoverable_by_category[item.get("category") or "UNSPECIFIED"].append(item)
    report = {
        "formatVersion": METRICS_FORMAT_VERSION,
        "definitions": {
            "rates": "classification, schema, review and coverage use all evaluated cases; evidence precision uses emitted evidence fields; semantic text rates use parsed JSON cases",
            "nonParsableOutputs": "count as failures for JSON, schema, classification, review and field coverage",
            "evidenceFieldPrecision": "expected section/index/field matches divided by all emitted evidence fields",
            "rounding": "rates use six decimal places; averages use three",
            "recoverableDiagnostics": "secondary analysis strips only an exact ```json ... ```<end_of_turn> wrapper; canonical metrics remain unchanged",
        },
        "categoryCounts": dict(sorted(category_counts.items())),
        "global": _aggregate(records, validator),
        "byCategory": {
            category: _aggregate(items, validator)
            for category, items in sorted(categories.items())
        },
        "recoverableDiagnostics": {
            "recoveredWrappedJson": recovered_count,
            "global": _aggregate(recoverable_records, validator),
            "byCategory": {
                category: _aggregate(items, validator)
                for category, items in sorted(recoverable_by_category.items())
            },
        },
    }
    if output_path is not None:
        write_json(output_path, report)
    return report
