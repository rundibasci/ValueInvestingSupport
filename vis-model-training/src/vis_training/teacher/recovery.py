"""Auditable recovery of schema-valid critic JSON wrapped in Markdown fences."""

import json
import re
from pathlib import Path
from typing import Any, Dict

from .errors import TeacherDataError
from .io import append_jsonl, iter_jsonl, read_object
from .validation import schema_errors

_JSON_FENCE = re.compile(r"^\s*```json\s*\n(?P<body>.*)\n```\s*$", re.DOTALL)


def recover_wrapped_critic(input_path: Path, output_path: Path, schema_path: Path) -> Dict[str, int]:
    """Create a new derived artifact; never alter or overwrite the source."""
    if Path(output_path).exists():
        raise TeacherDataError(f"Recovery output already exists: {output_path}")
    schema = read_object(schema_path)
    total = canonical = recovered = unrecoverable = 0
    for source in iter_jsonl(input_path):
        total += 1
        record = dict(source)
        if source.get("status") == "REVIEWED" and source.get("parsedReview") is not None:
            canonical += 1
        elif source.get("criticError") == "INVALID_JSON" and isinstance(source.get("rawReview"), str):
            match = _JSON_FENCE.fullmatch(source["rawReview"])
            try:
                parsed = json.loads(match.group("body")) if match else None
            except json.JSONDecodeError:
                parsed = None
            if isinstance(parsed, dict) and not schema_errors(parsed, schema):
                record["parsedReview"] = parsed
                record["status"] = "RECOVERED_REVIEW"
                record["criticError"] = "WRAPPED_JSON_RECOVERED"
                record["recovery"] = {"method": "STRIP_SINGLE_JSON_MARKDOWN_FENCE", "sourcePreserved": True}
                recovered += 1
            else:
                unrecoverable += 1
        else:
            unrecoverable += 1
        append_jsonl(output_path, record)
    return {"total": total, "canonical": canonical, "recovered": recovered, "unrecoverable": unrecoverable, "usable": canonical + recovered}
