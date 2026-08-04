"""Idempotent benchmark runner that preserves each first model response."""

import json
import time
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, Dict, Iterable, Optional

from .io import BenchmarkDataError, append_jsonl, iter_jsonl

RUN_FORMAT_VERSION = "1.0"


class GenerationBackend(ABC):
    @abstractmethod
    def generate(self, messages: list, *, max_new_tokens: int) -> Dict[str, Any]:
        """Return text plus optional inputTokens/outputTokens metadata."""

    @abstractmethod
    def manifest(self) -> Dict[str, Any]:
        """Return non-secret backend provenance."""


def _embedded_object(content: Any, field: str) -> Dict[str, Any]:
    if not isinstance(content, str):
        raise BenchmarkDataError(f"{field} content must be a JSON string")
    try:
        value = json.loads(content)
    except json.JSONDecodeError as error:
        raise BenchmarkDataError(f"{field} content is invalid JSON") from error
    if not isinstance(value, dict):
        raise BenchmarkDataError(f"{field} content root must be an object")
    return value


def _completed_ids(path: Path) -> set:
    if not Path(path).exists():
        return set()
    result = set()
    for record in iter_jsonl(path):
        example_id = record.get("exampleId")
        if not isinstance(example_id, str) or example_id in result:
            raise BenchmarkDataError("Run state contains a missing or duplicate exampleId")
        result.add(example_id)
    return result


class BenchmarkRunner:
    def __init__(self, backend: GenerationBackend, *, max_new_tokens: int = 1024) -> None:
        self.backend = backend
        self.max_new_tokens = max_new_tokens

    def run(
        self,
        dataset_path: Path,
        output_path: Path,
        *,
        limit: Optional[int] = None,
    ) -> Dict[str, int]:
        completed = _completed_ids(output_path)
        seen_dataset = set()
        processed = skipped = 0
        for document in iter_jsonl(dataset_path):
            metadata = document.get("metadata")
            messages = document.get("messages")
            if not isinstance(metadata, dict) or not isinstance(messages, list) or len(messages) != 3:
                raise BenchmarkDataError("Benchmark record contract is invalid")
            example_id = metadata.get("exampleId")
            if not isinstance(example_id, str) or example_id in seen_dataset:
                raise BenchmarkDataError("Benchmark contains a missing or duplicate exampleId")
            seen_dataset.add(example_id)
            if example_id in completed:
                skipped += 1
                continue
            if limit is not None and processed >= limit:
                break

            prompt_messages = messages[:2]
            expected = _embedded_object(messages[2].get("content"), "assistant")
            started = time.perf_counter()
            try:
                generated = self.backend.generate(
                    prompt_messages, max_new_tokens=self.max_new_tokens
                )
                raw_output = generated.get("text")
                if not isinstance(raw_output, str):
                    raise RuntimeError("Backend did not return text")
                error = None
            except Exception as generation_error:  # runner must continue per case
                generated = {}
                raw_output = None
                error = type(generation_error).__name__
            elapsed_ms = round((time.perf_counter() - started) * 1000, 3)
            parsed = None
            parse_error = None
            if raw_output is not None:
                try:
                    candidate = json.loads(raw_output)
                    if isinstance(candidate, dict):
                        parsed = candidate
                    else:
                        parse_error = "ROOT_NOT_OBJECT"
                except json.JSONDecodeError:
                    parse_error = "INVALID_JSON"
            result = {
                "formatVersion": RUN_FORMAT_VERSION,
                "exampleId": example_id,
                "category": metadata.get("benchmarkCategory"),
                "expected": expected,
                "rawOutput": raw_output,
                "parsedOutput": parsed,
                "parseError": parse_error,
                "generationError": error,
                "latencyMs": elapsed_ms,
                "inputTokens": generated.get("inputTokens"),
                "outputTokens": generated.get("outputTokens"),
            }
            append_jsonl(output_path, result)
            processed += 1
        return {"processed": processed, "skipped": skipped, "totalSeen": len(seen_dataset)}
