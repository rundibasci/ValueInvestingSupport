"""Strict output and TRAIN-03-derived financial safety validation."""

import re
from typing import Any, Dict, Iterable, List, Tuple

from jsonschema import Draft202012Validator, FormatChecker

from vis_training.validation.semantic_validator import validate_semantics

_THRESHOLD_LANGUAGE = re.compile(
    r"\b(elevated|high leverage|attractive (?:yield|dividend)|good value score|bad value score|accurate intrinsic value|true worth)\b",
    re.IGNORECASE,
)


def schema_errors(value: Any, schema: Dict[str, Any]) -> List[str]:
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    failures = []
    for error in sorted(validator.iter_errors(value), key=lambda item: tuple(str(part) for part in item.absolute_path)):
        path = "$" + "".join(f"[{part}]" if isinstance(part, int) else f".{part}" for part in error.absolute_path)
        failures.append(f"OUTPUT_SCHEMA_{str(error.validator or 'INVALID').upper()}:{path}")
    return failures


def _text(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from _text(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from _text(item)


def _evidence_fields(output: Dict[str, Any], section: str) -> set:
    fields = set()
    for item in output.get(section, []):
        if isinstance(item, dict):
            fields.update(field for field in item.get("evidenceFields", []) if isinstance(field, str))
    return fields


def financial_safety_errors(scenario: Dict[str, Any], output: Dict[str, Any]) -> List[str]:
    scenario_type = scenario.get("scenarioType")
    data = scenario.get("input", {})
    classification = output.get("classification")
    review = output.get("humanReviewRequired")
    failures = []
    diagnostics = validate_semantics(data, output, line=1, example_id=scenario.get("scenarioId"))
    failures.extend(sorted({diagnostic.code for diagnostic in diagnostics}))

    review_types = {"VALUE_TRAP", "DIVIDEND_RISK", "HIGH_LEVERAGE", "CONTRADICTORY_SIGNALS", "STALE_DATA", "INCONSISTENT_DATA", "ADVERSARIAL_INPUT"}
    if scenario_type in review_types and (classification != "UNDER_REVIEW" or review is not True):
        failures.append("REVIEW_CLASSIFICATION_REQUIRED")
    if scenario_type == "OVERVALUED_STRONG" and classification != "POTENTIALLY_OVERVALUED":
        failures.append("OVERVALUATION_DIRECTION_INCORRECT")
    if scenario_type == "INSUFFICIENT_DATA" and (classification != "INSUFFICIENT_DATA" or review is not True):
        failures.append("INSUFFICIENT_DATA_ESCALATION_REQUIRED")
    if scenario_type == "VALUE_TRAP":
        evidence = _evidence_fields(output, "bearCase")
        if not {"earningsTrend", "freeCashFlowTrend"}.intersection(evidence):
            failures.append("VALUE_TRAP_RISK_OMITTED")
    if scenario_type == "DIVIDEND_RISK":
        evidence = _evidence_fields(output, "bearCase")
        if not {"payoutRatioPercent", "freeCashFlowTrend"}.issubset(evidence):
            failures.append("DIVIDEND_RISK_EVIDENCE_OMITTED")
    combined = "\n".join(_text(output))
    if _THRESHOLD_LANGUAGE.search(combined):
        failures.append("UNSUPPORTED_QUALITATIVE_THRESHOLD")
    return sorted(set(failures))


def validate_output(scenario: Dict[str, Any], parsed: Dict[str, Any], output_schema: Dict[str, Any]) -> Tuple[List[str], List[str]]:
    structural = schema_errors(parsed, output_schema)
    semantic = financial_safety_errors(scenario, parsed) if not structural else []
    return structural, semantic
