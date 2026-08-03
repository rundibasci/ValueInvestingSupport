"""Streaming JSONL orchestration for TRAIN dataset validation."""

import json
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

from . import codes
from .models import Diagnostic, ValidationReport
from .schema_validator import (
    SchemaConfigurationError,
    build_validator,
    load_schema,
    validate_instance,
)
from .semantic_validator import validate_semantics


class DatasetConfigurationError(Exception):
    def __init__(self, code: str, path: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.path = path
        self.message = message


def _diagnostic(
    line: int,
    example_id: Optional[str],
    code: str,
    path: str,
    message: str,
) -> Diagnostic:
    return Diagnostic(line, example_id, code, path, "error", message)


def _parse_embedded(
    content: Any,
    *,
    line: int,
    example_id: Optional[str],
    path: str,
) -> Tuple[Optional[Dict[str, Any]], list]:
    if not isinstance(content, str):
        return None, [_diagnostic(line, example_id, codes.EMBEDDED_JSON_INVALID, path, "Embedded content must be a JSON string.")]
    stripped = content.strip()
    if not stripped.startswith("{") or not stripped.endswith("}"):
        return None, [_diagnostic(line, example_id, codes.TEXT_OUTSIDE_JSON, path, "Embedded content must contain only one JSON object.")]
    try:
        parsed = json.loads(stripped)
    except json.JSONDecodeError:
        return None, [_diagnostic(line, example_id, codes.EMBEDDED_JSON_INVALID, path, "Embedded content is not valid JSON.")]
    if not isinstance(parsed, dict):
        return None, [_diagnostic(line, example_id, codes.EMBEDDED_JSON_INVALID, path, "Embedded JSON root must be an object.")]
    return parsed, []


def _metadata(document: Dict[str, Any], line: int) -> Tuple[Optional[str], list]:
    metadata = document.get("metadata")
    if not isinstance(metadata, dict):
        return None, [_diagnostic(line, None, codes.METADATA_INVALID, "$.metadata", "Metadata must be an object.")]
    diagnostics = []
    for field in ("exampleId", "scenarioType", "source", "datasetVersion"):
        if not isinstance(metadata.get(field), str) or not metadata[field].strip():
            diagnostics.append(
                _diagnostic(line, None, codes.METADATA_INVALID, f"$.metadata.{field}", "Required metadata field is missing or invalid.")
            )
    example_id = metadata.get("exampleId") if isinstance(metadata.get("exampleId"), str) else None
    return example_id, diagnostics


def _validate_record(
    document: Dict[str, Any],
    *,
    line: int,
    input_validator: Any,
    output_validator: Any,
) -> Tuple[Optional[str], list]:
    example_id, diagnostics = _metadata(document, line)
    messages = document.get("messages")
    if not isinstance(messages, list) or len(messages) != 3:
        diagnostics.append(
            _diagnostic(line, example_id, codes.CONVERSATION_STRUCTURE_INVALID, "$.messages", "Conversation must contain exactly three messages.")
        )
        return example_id, diagnostics
    roles = [message.get("role") if isinstance(message, dict) else None for message in messages]
    if roles != ["system", "user", "assistant"] or any(not isinstance(message, dict) for message in messages):
        diagnostics.append(
            _diagnostic(line, example_id, codes.CONVERSATION_STRUCTURE_INVALID, "$.messages", "Conversation roles must be system, user, assistant in order.")
        )
        return example_id, diagnostics

    input_data, input_diagnostics = _parse_embedded(
        messages[1].get("content"), line=line, example_id=example_id, path="$.messages[1].content"
    )
    output_data, output_diagnostics = _parse_embedded(
        messages[2].get("content"), line=line, example_id=example_id, path="$.messages[2].content"
    )
    diagnostics.extend(input_diagnostics)
    diagnostics.extend(output_diagnostics)
    if input_data is None or output_data is None:
        return example_id, diagnostics

    diagnostics.extend(
        validate_instance(
            input_data,
            input_validator,
            line=line,
            example_id=example_id,
            code=codes.INPUT_SCHEMA_INVALID,
            prefix="$.user",
        )
    )
    diagnostics.extend(
        validate_instance(
            output_data,
            output_validator,
            line=line,
            example_id=example_id,
            code=codes.OUTPUT_SCHEMA_INVALID,
            prefix="$.assistant",
        )
    )
    diagnostics.extend(
        validate_semantics(input_data, output_data, line=line, example_id=example_id)
    )
    return example_id, diagnostics


def validate_dataset(
    dataset_path: Path,
    input_schema_path: Path,
    output_schema_path: Path,
) -> ValidationReport:
    dataset_path = Path(dataset_path)
    if not dataset_path.is_file():
        raise DatasetConfigurationError(
            codes.DATASET_NOT_FOUND, str(dataset_path), "Dataset file was not found."
        )
    try:
        input_schema = load_schema(Path(input_schema_path))
        output_schema = load_schema(Path(output_schema_path))
    except SchemaConfigurationError as error:
        raise DatasetConfigurationError(error.code, error.path, error.message) from error

    input_validator = build_validator(input_schema)
    output_validator = build_validator(output_schema)
    report = ValidationReport(dataset=str(dataset_path))
    seen_ids = set()

    try:
        dataset_file = dataset_path.open("r", encoding="utf-8")
    except OSError as error:
        raise DatasetConfigurationError(
            codes.DATASET_IO_ERROR, str(dataset_path), "Dataset file could not be read."
        ) from error

    try:
        with dataset_file:
            for line_number, raw_line in enumerate(dataset_file, start=1):
                if not raw_line.strip():
                    continue
                report.records += 1
                line_diagnostics = []
                try:
                    document = json.loads(raw_line)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    line_diagnostics.append(
                        _diagnostic(line_number, None, codes.JSONL_PARSE_ERROR, "$", "JSONL line is not valid JSON.")
                    )
                    report.invalid += 1
                    report.diagnostics.extend(line_diagnostics)
                    continue
                if not isinstance(document, dict):
                    line_diagnostics.append(
                        _diagnostic(line_number, None, codes.JSONL_RECORD_NOT_OBJECT, "$", "JSONL record root must be an object.")
                    )
                    report.invalid += 1
                    report.diagnostics.extend(line_diagnostics)
                    continue

                example_id, record_diagnostics = _validate_record(
                    document,
                    line=line_number,
                    input_validator=input_validator,
                    output_validator=output_validator,
                )
                line_diagnostics.extend(record_diagnostics)
                if example_id:
                    if example_id in seen_ids:
                        line_diagnostics.append(
                            _diagnostic(line_number, example_id, codes.DUPLICATE_EXAMPLE_ID, "$.metadata.exampleId", "exampleId must be unique within the dataset.")
                        )
                    else:
                        seen_ids.add(example_id)

                if any(item.severity == "error" for item in line_diagnostics):
                    report.invalid += 1
                else:
                    report.valid += 1
                report.diagnostics.extend(line_diagnostics)
    except (OSError, UnicodeError) as error:
        raise DatasetConfigurationError(
            codes.DATASET_IO_ERROR, str(dataset_path), "Dataset file could not be read completely."
        ) from error

    if report.records == 0:
        report.diagnostics.append(
            Diagnostic(None, None, codes.EMPTY_DATASET, "$", "error", "Dataset contains no records.")
        )

    return report
