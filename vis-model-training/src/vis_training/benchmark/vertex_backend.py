"""Vertex AI Gemini generation backend (TA3).

Implements the same `GenerationBackend` interface as `HuggingFaceBackend`
so the existing `BenchmarkRunner`/`metrics`/`review` pipeline (TRAIN-03)
runs unchanged against Vertex AI instead of a local/RunPod checkpoint.

Configuration (model id, region, decoding, the derived responseSchema) is
loaded from `config/vertex-gemini-v1.json` (TA2) — never hardcoded here, so
config/vertex-gemini-v1.json remains the single source of truth for the
request shape. `GOOGLE_CLOUD_PROJECT` and Vertex AI service-account
credentials (Application Default Credentials) come from the environment,
matching the handling class every other credential in this project already
uses (specs/mission.md Principle 7; docs/governance/data-policy.md) — never
a value read from or written to this config file.

No call is made anywhere in this module at import time or construction
time beyond building the client; the first live call happens only when
`.generate()` is invoked (TA3's actual benchmark run, not this module's
own tests, which mock the client).
"""

from __future__ import annotations

import json
import os
import platform
from pathlib import Path
from typing import Any, Dict

from .runner import GenerationBackend


class VertexBackendConfigurationError(Exception):
    pass


class VertexBackend(GenerationBackend):
    def __init__(
        self,
        config_path: Path,
        *,
        project_id: str | None = None,
        client_factory=None,
    ) -> None:
        config = json.loads(Path(config_path).read_text(encoding="utf-8"))
        model_config = config.get("model", {})
        generation_config = config.get("generationConfig", {})

        self.model_id = model_config.get("modelId")
        self.location = model_config.get("location")
        if not self.model_id or not self.location:
            raise VertexBackendConfigurationError(
                f"{config_path} is missing model.modelId or model.location"
            )

        self.temperature = generation_config.get("temperature")
        self.response_mime_type = generation_config.get("responseMimeType")
        self.response_schema = generation_config.get("responseSchema")
        if self.temperature is None or not self.response_mime_type or not self.response_schema:
            raise VertexBackendConfigurationError(
                f"{config_path} is missing generationConfig.temperature/"
                "responseMimeType/responseSchema"
            )
        # Optional: gemini-2.5-flash performs internal "thinking" by default,
        # sharing maxOutputTokens with the visible response (confirmed live,
        # 2026-08-28 — see config/vertex-gemini-v1.json's thinkingBudgetNote).
        # None (key absent) means "let the API use its own default", not 0.
        self.thinking_budget = generation_config.get("thinkingBudget")

        grounding_tools = config.get("groundingTools", [])
        if grounding_tools:
            raise VertexBackendConfigurationError(
                "groundingTools must be empty — this integration reasons only over "
                "VIS-supplied context (specs/mission.md, ADR-002)"
            )

        self.project_id = project_id or os.environ.get("GOOGLE_CLOUD_PROJECT")
        if not self.project_id:
            raise VertexBackendConfigurationError(
                "GOOGLE_CLOUD_PROJECT is required (env var or project_id argument); "
                "Vertex AI credentials are never read from config/vertex-gemini-v1.json"
            )

        if client_factory is None:
            from google import genai

            client_factory = genai.Client
        self._client = client_factory(
            vertexai=True, project=self.project_id, location=self.location
        )
        self._config_path = str(config_path)

    def generate(self, messages: list, *, max_new_tokens: int) -> Dict[str, Any]:
        from google.genai import types

        system_content = None
        user_content = None
        for message in messages:
            if message["role"] == "system":
                system_content = message["content"]
            elif message["role"] == "user":
                user_content = message["content"]
        if user_content is None:
            raise ValueError("messages must include a 'user' role entry")

        thinking_config = None
        if self.thinking_budget is not None:
            thinking_config = types.ThinkingConfig(thinking_budget=self.thinking_budget)

        generation_config = types.GenerateContentConfig(
            system_instruction=system_content,
            temperature=self.temperature,
            max_output_tokens=max_new_tokens,
            response_mime_type=self.response_mime_type,
            response_schema=self.response_schema,
            thinking_config=thinking_config,
        )
        response = self._client.models.generate_content(
            model=self.model_id,
            contents=user_content,
            config=generation_config,
        )
        usage = response.usage_metadata
        return {
            "text": response.text,
            "inputTokens": getattr(usage, "prompt_token_count", None),
            "outputTokens": getattr(usage, "candidates_token_count", None),
        }

    def manifest(self) -> Dict[str, Any]:
        return {
            "provider": "Vertex AI",
            "modelId": self.model_id,
            "location": self.location,
            "projectId": self.project_id,
            "temperature": self.temperature,
            "responseMimeType": self.response_mime_type,
            "thinkingBudget": self.thinking_budget,
            "configPath": self._config_path,
            "python": platform.python_version(),
        }
