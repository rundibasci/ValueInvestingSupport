"""Canonical serialization and atomic artifact writes for TRAIN-04."""

import hashlib
import json
from pathlib import Path
from typing import Any, Dict, Iterable, List

from .errors import ScenarioConfigurationError


def canonical_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def dataset_bytes(records: Iterable[Dict[str, Any]]) -> bytes:
    return ("".join(canonical_json(record) + "\n" for record in records)).encode("utf-8")


def sha256_bytes(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def load_object(path: Path) -> Dict[str, Any]:
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as error:
        raise ScenarioConfigurationError(f"Cannot read JSON object: {path}") from error
    if not isinstance(value, dict):
        raise ScenarioConfigurationError(f"JSON root must be an object: {path}")
    return value


def load_jsonl(path: Path) -> List[Dict[str, Any]]:
    records = []
    try:
        lines = Path(path).read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as error:
        raise ScenarioConfigurationError(f"Cannot read JSONL: {path}") from error
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            raise ScenarioConfigurationError(f"Invalid JSONL at line {line_number}: {path}") from error
        if not isinstance(value, dict):
            raise ScenarioConfigurationError(f"JSONL record is not an object at line {line_number}: {path}")
        records.append(value)
    return records


def write_artifacts_atomic(dataset_path: Path, report_path: Path, content: bytes, report: Dict[str, Any]) -> None:
    dataset_path = Path(dataset_path)
    report_path = Path(report_path)
    dataset_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.parent.mkdir(parents=True, exist_ok=True)
    dataset_tmp = dataset_path.with_suffix(dataset_path.suffix + ".tmp")
    report_tmp = report_path.with_suffix(report_path.suffix + ".tmp")
    try:
        dataset_tmp.write_bytes(content)
        report_tmp.write_text(json.dumps(report, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        dataset_tmp.replace(dataset_path)
        report_tmp.replace(report_path)
    finally:
        for temporary in (dataset_tmp, report_tmp):
            if temporary.exists():
                temporary.unlink()
