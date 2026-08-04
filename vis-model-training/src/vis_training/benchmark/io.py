"""Strict JSON/JSONL helpers shared by TRAIN-03 tooling."""

import json
from pathlib import Path
from typing import Any, Dict, Iterator


class BenchmarkDataError(ValueError):
    """Raised when a benchmark artifact is malformed or inconsistent."""


def read_json(path: Path) -> Dict[str, Any]:
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise BenchmarkDataError(f"Cannot read JSON artifact: {path}") from error
    if not isinstance(value, dict):
        raise BenchmarkDataError(f"JSON artifact root must be an object: {path}")
    return value


def write_json(path: Path, value: Dict[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(
        json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    temporary.replace(path)


def iter_jsonl(path: Path) -> Iterator[Dict[str, Any]]:
    try:
        handle = Path(path).open("r", encoding="utf-8")
    except OSError as error:
        raise BenchmarkDataError(f"Cannot read JSONL artifact: {path}") from error
    with handle:
        for line_number, raw_line in enumerate(handle, start=1):
            if not raw_line.strip():
                continue
            try:
                value = json.loads(raw_line)
            except json.JSONDecodeError as error:
                raise BenchmarkDataError(
                    f"Invalid JSONL at line {line_number}: {path}"
                ) from error
            if not isinstance(value, dict):
                raise BenchmarkDataError(
                    f"JSONL record at line {line_number} is not an object: {path}"
                )
            yield value


def append_jsonl(path: Path, value: Dict[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(value, sort_keys=True, ensure_ascii=False) + "\n")
