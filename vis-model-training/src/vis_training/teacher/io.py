"""Strict and atomic artifact helpers for TRAIN-05."""

import hashlib
import json
from pathlib import Path
from typing import Any, Dict, Iterable, Iterator, List

from .errors import TeacherConfigurationError, TeacherDataError


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def sha256_file(path: Path) -> str:
    try:
        return sha256_bytes(Path(path).read_bytes())
    except OSError as error:
        raise TeacherConfigurationError(f"Cannot hash artifact: {path}") from error


def read_object(path: Path) -> Dict[str, Any]:
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise TeacherConfigurationError(f"Cannot read JSON object: {path}") from error
    if not isinstance(value, dict):
        raise TeacherConfigurationError(f"JSON root must be an object: {path}")
    return value


def iter_jsonl(path: Path) -> Iterator[Dict[str, Any]]:
    try:
        handle = Path(path).open("r", encoding="utf-8")
    except OSError as error:
        raise TeacherDataError(f"Cannot read JSONL: {path}") from error
    with handle:
        for line_number, line in enumerate(handle, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as error:
                raise TeacherDataError(f"Invalid JSONL at line {line_number}: {path}") from error
            if not isinstance(value, dict):
                raise TeacherDataError(f"JSONL record must be an object at line {line_number}: {path}")
            yield value


def append_jsonl(path: Path, value: Dict[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as handle:
        handle.write(canonical_json(value) + "\n")


def write_json(path: Path, value: Dict[str, Any]) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    try:
        temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        temporary.replace(path)
    finally:
        if temporary.exists():
            temporary.unlink()


def load_unique(path: Path, key: str) -> Dict[str, Dict[str, Any]]:
    if not Path(path).exists():
        return {}
    records = {}
    for record in iter_jsonl(path):
        identity = record.get(key)
        if not isinstance(identity, str) or identity in records:
            raise TeacherDataError(f"Missing or duplicate {key}: {path}")
        records[identity] = record
    return records
