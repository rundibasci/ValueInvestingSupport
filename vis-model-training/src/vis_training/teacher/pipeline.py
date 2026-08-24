"""Resumable, append-only teacher candidate generation."""

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Dict, Optional, Protocol

from jsonschema import Draft202012Validator, FormatChecker

from .config import build_manifest, load_local_config
from .errors import TeacherDataError, TeacherManifestMismatch
from .io import append_jsonl, canonical_json, load_unique, read_object, sha256_file, write_json
from .validation import validate_output


class TeacherBackend(Protocol):
    def generate(self, messages, *, candidate_id: str, seed: int, generation_parameters: Dict[str, Any]) -> Dict[str, Any]: ...
    def manifest(self) -> Dict[str, Any]: ...


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def _seed(base: int, candidate_index: int) -> int:
    return (int(base) + candidate_index * 1_000_003) % (2**63 - 1)


class CandidateRunner:
    def __init__(self, root: Path, config_path: Path, backend: TeacherBackend, *, clock: Callable[[], str] = _utc_now):
        self.root = Path(root)
        self.config_path = Path(config_path)
        self.backend = backend
        self.clock = clock
        loaded = load_local_config(self.root, self.config_path)
        self.config = loaded["config"]
        self.paths = loaded["paths"]
        self.teacher_prompt = self.paths["teacherPromptPath"].read_text(encoding="utf-8")
        self.output_schema = read_object(self.paths["outputSchemaPath"])
        self.candidate_schema = read_object(self.paths["candidateSchemaPath"])

    def _manifest(self, run_id: str, hardware_profile: str) -> Dict[str, Any]:
        return build_manifest(self.root, self.config_path, self.backend.manifest(), run_id=run_id, hardware_profile=hardware_profile)

    def run(self, scenarios_path: Path, output_path: Path, manifest_path: Path, *, run_id: str = "train-05-local", hardware_profile: str = "LOCAL_FAKE", limit: Optional[int] = None) -> Dict[str, int]:
        expected_manifest = self._manifest(run_id, hardware_profile)
        if Path(manifest_path).exists():
            if read_object(manifest_path) != expected_manifest:
                raise TeacherManifestMismatch("Existing run manifest is incompatible")
        else:
            write_json(manifest_path, expected_manifest)
        existing = load_unique(output_path, "candidateId")
        processed = skipped = scenarios_seen = 0
        from .io import iter_jsonl
        for scenario in iter_jsonl(scenarios_path):
            if limit is not None and scenarios_seen >= limit:
                break
            scenario_id = scenario.get("scenarioId")
            if not isinstance(scenario_id, str) or not scenario_id.startswith("SCN-"):
                raise TeacherDataError("Scenario has invalid scenarioId")
            scenarios_seen += 1
            for index in range(1, self.config["candidateCountPerScenario"] + 1):
                candidate_id = f"TC-{scenario_id}-{index:02d}"
                if candidate_id in existing:
                    skipped += 1
                    continue
                record = self._generate(scenario, candidate_id, index, expected_manifest)
                failures = sorted(error.message for error in Draft202012Validator(self.candidate_schema, format_checker=FormatChecker()).iter_errors(record))
                if failures:
                    raise TeacherDataError("Generated candidate record violates its contract: " + "; ".join(failures))
                append_jsonl(output_path, record)
                existing[candidate_id] = record
                processed += 1
        return {"scenariosSeen": scenarios_seen, "processed": processed, "skipped": skipped, "candidateSlots": processed + skipped}

    def _generate(self, scenario: Dict[str, Any], candidate_id: str, index: int, manifest: Dict[str, Any]) -> Dict[str, Any]:
        # Candidate identity belongs to pipeline provenance, not to the model's output contract.
        # Keeping it out of the visible payload prevents the model from copying it into the
        # thesis object when the output schema has additionalProperties=false.
        payload = canonical_json({"scenario": scenario, "outputSchema": self.output_schema})
        messages = [{"role": "system", "content": self.teacher_prompt}, {"role": "user", "content": payload}]
        raw = parsed = generation_error = parse_error = None
        structural_errors, semantic_errors = [], []
        input_tokens = output_tokens = latency_ms = None
        try:
            result = self.backend.generate(
                messages,
                candidate_id=candidate_id,
                seed=_seed(scenario.get("seed", 0), index),
                generation_parameters=self.config["decoding"],
            )
            raw = result.get("text")
            input_tokens, output_tokens, latency_ms = result.get("inputTokens"), result.get("outputTokens"), result.get("latencyMs")
            try:
                parsed = json.loads(raw)
                if not isinstance(parsed, dict):
                    raise ValueError("root")
            except (json.JSONDecodeError, TypeError, ValueError):
                parse_error = "INVALID_JSON"
        except Exception as error:  # backend boundary: never persist provider messages
            generation_error = type(error).__name__
        if parsed is not None:
            structural_errors, semantic_errors = validate_output(scenario, parsed, self.output_schema)
        if generation_error:
            status = "GENERATION_FAILED"
        elif parse_error:
            status = "PARSE_REJECTED"
        elif structural_errors:
            status = "STRUCTURAL_REJECTED"
        elif semantic_errors:
            status = "SEMANTIC_REJECTED"
        else:
            status = "CRITIC_PENDING"
        backend = manifest["backend"]
        return {
            "formatVersion": "1.0", "candidateId": candidate_id, "scenarioId": scenario["scenarioId"],
            "scenarioType": scenario["scenarioType"], "difficulty": scenario["difficulty"], "candidateIndex": index,
            "status": status, "rawOutput": raw, "parsedOutput": parsed, "generationError": generation_error,
            "parseError": parse_error, "structuralErrors": structural_errors, "semanticErrors": semantic_errors,
            "criticEligible": parsed is not None, "inputTokens": input_tokens, "outputTokens": output_tokens, "latencyMs": latency_ms,
            "provenance": {"source": "SYNTHETIC_TEACHER", "teacherProvider": backend["provider"], "teacherModel": backend["model"],
                           "teacherModelVersion": backend["revision"], "promptVersion": self.config["teacherPromptVersion"],
                           "promptSha256": sha256_file(self.paths["teacherPromptPath"]), "generationParameters": self.config["decoding"],
                           "generatedAt": self.clock(), "licenseReviewId": self.config["licenseReviewId"], "runId": manifest["runId"],
                           "hardwareProfile": manifest["hardwareProfile"]},
        }
