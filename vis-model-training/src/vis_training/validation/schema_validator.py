"""Draft 2020-12 schema loading and sanitized instance validation."""

import json
from pathlib import Path
from typing import Any, Dict, Iterable, Optional

from jsonschema import Draft202012Validator, FormatChecker
from jsonschema.exceptions import SchemaError

from . import codes
from .models import Diagnostic


class SchemaConfigurationError(Exception):
    def __init__(self, code: str, path: str, message: str) -> None:
        super().__init__(message)
        self.code = code
        self.path = path
        self.message = message


def load_schema(path: Path) -> Dict[str, Any]:
    if not path.is_file():
        raise SchemaConfigurationError(
            codes.SCHEMA_NOT_FOUND, str(path), "Schema file was not found."
        )
    try:
        content = path.read_text(encoding="utf-8")
    except OSError as error:
        raise SchemaConfigurationError(
            codes.SCHEMA_NOT_FOUND, str(path), "Schema file could not be read."
        ) from error
    try:
        schema = json.loads(content)
    except json.JSONDecodeError as error:
        raise SchemaConfigurationError(
            codes.SCHEMA_PARSE_ERROR, str(path), "Schema is not valid JSON."
        ) from error
    if not isinstance(schema, dict):
        raise SchemaConfigurationError(
            codes.SCHEMA_DEFINITION_INVALID,
            str(path),
            "Schema root must be an object.",
        )
    try:
        Draft202012Validator.check_schema(schema)
    except SchemaError as error:
        raise SchemaConfigurationError(
            codes.SCHEMA_DEFINITION_INVALID,
            str(path),
            "Schema is not a valid Draft 2020-12 definition.",
        ) from error
    return schema


def build_validator(schema: Dict[str, Any]) -> Draft202012Validator:
    return Draft202012Validator(schema, format_checker=FormatChecker())


def _json_path(parts: Iterable[Any], prefix: str) -> str:
    path = prefix
    for part in parts:
        path += f"[{part}]" if isinstance(part, int) else f".{part}"
    return path


def validate_instance(
    instance: Any,
    validator: Draft202012Validator,
    *,
    line: int,
    example_id: Optional[str],
    code: str,
    prefix: str,
) -> list:
    diagnostics = []
    errors = sorted(
        validator.iter_errors(instance),
        key=lambda error: (tuple(str(part) for part in error.absolute_path), error.validator or ""),
    )
    for error in errors:
        rule = error.validator or "schema"
        diagnostics.append(
            Diagnostic(
                line=line,
                example_id=example_id,
                code=code,
                path=_json_path(error.absolute_path, prefix),
                severity="error",
                message=f"Schema validation failed ({rule}).",
            )
        )
    return diagnostics
