"""Semantic grounding and decision-support rules for TRAIN examples."""

import re
from typing import Any, Dict, Iterable, Optional

from . import codes
from .models import Diagnostic

_RECOMMENDATION = re.compile(r"\b(buy|sell|hold)\b", re.IGNORECASE)
_MARKDOWN = re.compile(r"```|(^|\n)\s{0,3}(#{1,6}|[-*])\s", re.MULTILINE)
_NUMBER = re.compile(r"(?<![A-Za-z])[-+]?\d+(?:\.\d+)?")
_REVIEW_DATA_QUALITIES = {"INSUFFICIENT", "INCONSISTENT", "STALE"}


def _text_values(value: Any) -> Iterable[str]:
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from _text_values(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from _text_values(item)


def validate_semantics(
    input_data: Dict[str, Any],
    output_data: Dict[str, Any],
    *,
    line: int,
    example_id: Optional[str],
) -> list:
    diagnostics = []

    for section in ("bullCase", "bearCase"):
        evidence_items = output_data.get(section, [])
        if not isinstance(evidence_items, list):
            continue
        for index, evidence in enumerate(evidence_items):
            if not isinstance(evidence, dict):
                continue
            fields = evidence.get("evidenceFields", [])
            if not isinstance(fields, list):
                continue
            for field_index, field in enumerate(fields):
                path = f"$.assistant.{section}[{index}].evidenceFields[{field_index}]"
                if not isinstance(field, str) or field not in input_data:
                    diagnostics.append(
                        Diagnostic(line, example_id, codes.EVIDENCE_FIELD_MISSING, path, "error", "Evidence field is absent from input.")
                    )
                elif input_data[field] is None:
                    diagnostics.append(
                        Diagnostic(line, example_id, codes.EVIDENCE_FIELD_NULL, path, "error", "Evidence field is null in input.")
                    )

    quality = input_data.get("dataQuality")
    classification = output_data.get("classification")
    review = output_data.get("humanReviewRequired")
    if quality == "INSUFFICIENT" and classification != "INSUFFICIENT_DATA":
        diagnostics.append(
            Diagnostic(line, example_id, codes.INSUFFICIENT_CLASSIFICATION_REQUIRED, "$.assistant.classification", "error", "Insufficient data requires INSUFFICIENT_DATA classification.")
        )

    warnings = input_data.get("deterministicWarnings", [])
    contradictory = isinstance(warnings, list) and "CONTRADICTORY_SIGNALS" in warnings
    if (quality in _REVIEW_DATA_QUALITIES or contradictory) and review is not True:
        diagnostics.append(
            Diagnostic(line, example_id, codes.HUMAN_REVIEW_REQUIRED, "$.assistant.humanReviewRequired", "error", "Problematic data requires human review.")
        )

    trends = [
        input_data.get("revenueTrend"),
        input_data.get("earningsTrend"),
        input_data.get("freeCashFlowTrend"),
    ]
    if "STRONGLY_DECLINING" in trends and not output_data.get("bearCase"):
        diagnostics.append(
            Diagnostic(line, example_id, codes.BEAR_CASE_REQUIRED, "$.assistant.bearCase", "error", "Strongly declining trends require a bear case.")
        )

    output_text = "\n".join(_text_values(output_data))
    if _RECOMMENDATION.search(output_text):
        diagnostics.append(
            Diagnostic(line, example_id, codes.PROHIBITED_RECOMMENDATION, "$.assistant", "error", "Operational recommendation is prohibited.")
        )
    if _MARKDOWN.search(output_text):
        diagnostics.append(
            Diagnostic(line, example_id, codes.MARKDOWN_PROHIBITED, "$.assistant", "error", "Markdown is prohibited.")
        )

    input_numbers = {
        float(value)
        for value in input_data.values()
        if isinstance(value, (int, float)) and not isinstance(value, bool)
    }
    for match in _NUMBER.finditer(output_text):
        if float(match.group(0)) not in input_numbers:
            diagnostics.append(
                Diagnostic(line, example_id, codes.UNSUPPORTED_NUMERIC_CLAIM, "$.assistant", "error", "Output text contains a number not supplied by the input.")
            )
            break

    return diagnostics
