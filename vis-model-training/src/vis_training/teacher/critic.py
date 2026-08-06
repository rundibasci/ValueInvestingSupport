"""Independent, immutable critic pass for parseable candidates."""

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, Protocol

from .config import load_local_config
from .errors import TeacherDataError
from .io import append_jsonl, canonical_json, iter_jsonl, load_unique, read_object, sha256_file
from .validation import schema_errors


class CriticBackend(Protocol):
    def review(self, messages, *, max_new_tokens: int) -> Dict[str, Any]: ...
    def manifest(self) -> Dict[str, Any]: ...


class CriticRunner:
    def __init__(self, root: Path, config_path: Path, backend: CriticBackend):
        self.root, self.config_path, self.backend = Path(root), Path(config_path), backend
        loaded = load_local_config(self.root, self.config_path)
        self.config, self.paths = loaded["config"], loaded["paths"]
        self.prompt = self.paths["criticPromptPath"].read_text(encoding="utf-8")
        self.schema = read_object(self.paths["criticSchemaPath"])

    def run(self, scenarios_path: Path, candidates_path: Path, output_path: Path) -> Dict[str, int]:
        scenarios = {item["scenarioId"]: item for item in iter_jsonl(scenarios_path)}
        candidates = load_unique(candidates_path, "candidateId")
        existing = load_unique(output_path, "criticId")
        processed = skipped = ineligible = 0
        for candidate_id, candidate in candidates.items():
            if not candidate.get("criticEligible"):
                ineligible += 1
                continue
            critic_id = f"CR-{candidate_id}"
            if critic_id in existing:
                skipped += 1
                continue
            scenario = scenarios.get(candidate["scenarioId"])
            if scenario is None:
                raise TeacherDataError(f"Candidate references unknown scenario: {candidate['scenarioId']}")
            record = self._review(critic_id, candidate, scenario)
            append_jsonl(output_path, record)
            existing[critic_id] = record
            processed += 1
        return {"processed": processed, "skipped": skipped, "ineligible": ineligible, "eligible": processed + skipped}

    def _review(self, critic_id: str, candidate: Dict[str, Any], scenario: Dict[str, Any]) -> Dict[str, Any]:
        payload = {"candidateId": candidate["candidateId"], "scenario": scenario, "candidateOutput": candidate["parsedOutput"],
                   "deterministicErrors": candidate["structuralErrors"] + candidate["semanticErrors"], "reviewSchema": self.schema}
        raw = parsed = error = None
        input_tokens = output_tokens = latency_ms = None
        try:
            result = self.backend.review([{"role": "system", "content": self.prompt}, {"role": "user", "content": canonical_json(payload)}],
                                         max_new_tokens=self.config["decoding"]["maxNewTokens"])
            raw = result.get("text")
            input_tokens, output_tokens, latency_ms = result.get("inputTokens"), result.get("outputTokens"), result.get("latencyMs")
            try:
                parsed = json.loads(raw)
                failures = schema_errors(parsed, self.schema)
                if failures:
                    error, parsed = "INVALID_CRITIC_SCHEMA", None
            except (json.JSONDecodeError, TypeError):
                error = "INVALID_JSON"
        except Exception as backend_error:
            error = type(backend_error).__name__
        backend = self.backend.manifest()
        return {"formatVersion": "1.0", "criticId": critic_id, "candidateId": candidate["candidateId"],
                "status": "REVIEWED" if parsed is not None else "CRITIC_FAILED", "rawReview": raw, "parsedReview": parsed,
                "criticError": error, "inputTokens": input_tokens, "outputTokens": output_tokens, "latencyMs": latency_ms,
                "provenance": {"criticProvider": backend["provider"], "criticModel": backend["model"], "criticModelVersion": backend["revision"],
                               "promptVersion": "critic-prompt-v1", "promptSha256": sha256_file(self.paths["criticPromptPath"]),
                               "reviewedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")}}
