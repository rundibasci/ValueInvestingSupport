"""VertexBackend tests. All Vertex AI calls are mocked via client_factory —
no test in this module makes a network call or requires real credentials,
matching this project's test-isolation discipline for third-party APIs
(specs/tech-stack.md: default tests mock InvestmentThesisClient / never
call live Vertex AI)."""

import json
from pathlib import Path
from types import SimpleNamespace

import pytest

from vis_training.benchmark.vertex_backend import (
    VertexBackend,
    VertexBackendConfigurationError,
)

ROOT = Path(__file__).resolve().parents[2]
CONFIG_PATH = ROOT / "config" / "vertex-gemini-v1.json"


class _FakeModels:
    def __init__(self, response):
        self._response = response
        self.calls = []

    def generate_content(self, *, model, contents, config):
        self.calls.append({"model": model, "contents": contents, "config": config})
        return self._response


class _FakeClient:
    def __init__(self, response):
        self.models = _FakeModels(response)


def _fake_response(text='{"classification":"FAIRLY_VALUED"}', prompt_tokens=42, output_tokens=7):
    usage = SimpleNamespace(prompt_token_count=prompt_tokens, candidates_token_count=output_tokens)
    return SimpleNamespace(text=text, usage_metadata=usage)


def _make_backend(response=None, **overrides):
    fake_client = _FakeClient(response or _fake_response())
    kwargs = {"project_id": "test-project", "client_factory": lambda **_: fake_client}
    kwargs.update(overrides)
    backend = VertexBackend(CONFIG_PATH, **kwargs)
    return backend, fake_client


def test_loads_real_config_and_builds_client():
    backend, fake_client = _make_backend()
    assert backend.model_id == "gemini-2.5-flash"
    assert backend.location == "europe-west1"
    assert backend.temperature == 0.0


def test_generate_returns_text_and_token_counts():
    backend, fake_client = _make_backend()
    result = backend.generate(
        [
            {"role": "system", "content": "system instructions"},
            {"role": "user", "content": '{"symbol":"TEST"}'},
        ],
        max_new_tokens=1024,
    )
    assert result["text"] == '{"classification":"FAIRLY_VALUED"}'
    assert result["inputTokens"] == 42
    assert result["outputTokens"] == 7


def test_generate_passes_system_instruction_and_user_content_separately():
    backend, fake_client = _make_backend()
    backend.generate(
        [
            {"role": "system", "content": "SYSTEM_TEXT"},
            {"role": "user", "content": "USER_TEXT"},
        ],
        max_new_tokens=512,
    )
    call = fake_client.models.calls[0]
    assert call["model"] == "gemini-2.5-flash"
    assert call["contents"] == "USER_TEXT"
    assert call["config"].system_instruction == "SYSTEM_TEXT"


def test_generate_forwards_config_generation_settings():
    backend, fake_client = _make_backend()
    backend.generate(
        [{"role": "system", "content": "s"}, {"role": "user", "content": "u"}],
        max_new_tokens=999,
    )
    call = fake_client.models.calls[0]
    assert call["config"].temperature == 0.0
    assert call["config"].max_output_tokens == 999
    assert call["config"].response_mime_type == "application/json"
    assert call["config"].response_schema == backend.response_schema


def test_generate_disables_thinking_per_checked_in_config():
    # Real config/vertex-gemini-v1.json pins thinkingBudget=0 (TA3's third
    # live call observed finish_reason=MAX_TOKENS with thoughts_token_count
    # dominating the budget before this was set — see thinkingBudgetNote).
    backend, fake_client = _make_backend()
    assert backend.thinking_budget == 0
    backend.generate(
        [{"role": "system", "content": "s"}, {"role": "user", "content": "u"}],
        max_new_tokens=999,
    )
    call = fake_client.models.calls[0]
    assert call["config"].thinking_config.thinking_budget == 0


def test_generate_omits_thinking_config_when_absent_from_config(tmp_path):
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    del config["generationConfig"]["thinkingBudget"]
    no_thinking_config_path = tmp_path / "no-thinking-budget.json"
    no_thinking_config_path.write_text(json.dumps(config), encoding="utf-8")

    fake_client = _FakeClient(_fake_response())
    backend = VertexBackend(
        no_thinking_config_path, project_id="test-project", client_factory=lambda **_: fake_client
    )
    assert backend.thinking_budget is None
    backend.generate(
        [{"role": "system", "content": "s"}, {"role": "user", "content": "u"}],
        max_new_tokens=999,
    )
    call = fake_client.models.calls[0]
    assert call["config"].thinking_config is None


def test_generate_requires_user_message():
    backend, fake_client = _make_backend()
    with pytest.raises(ValueError):
        backend.generate([{"role": "system", "content": "s"}], max_new_tokens=10)


def test_manifest_contains_no_credential_material():
    backend, fake_client = _make_backend()
    manifest = backend.manifest()
    assert manifest["modelId"] == "gemini-2.5-flash"
    assert manifest["projectId"] == "test-project"
    dumped = json.dumps(manifest)
    assert "credential" not in dumped.lower()
    assert "key" not in dumped.lower()
    assert "token" not in dumped.lower()


def test_missing_project_id_raises_configuration_error(monkeypatch):
    monkeypatch.delenv("GOOGLE_CLOUD_PROJECT", raising=False)
    with pytest.raises(VertexBackendConfigurationError):
        VertexBackend(CONFIG_PATH, client_factory=lambda **_: _FakeClient(_fake_response()))


def test_project_id_falls_back_to_env_var(monkeypatch):
    monkeypatch.setenv("GOOGLE_CLOUD_PROJECT", "env-project")
    backend = VertexBackend(CONFIG_PATH, client_factory=lambda **_: _FakeClient(_fake_response()))
    assert backend.project_id == "env-project"


def test_client_factory_receives_vertexai_project_location():
    captured = {}

    def factory(**kwargs):
        captured.update(kwargs)
        return _FakeClient(_fake_response())

    VertexBackend(CONFIG_PATH, project_id="p", client_factory=factory)
    assert captured == {"vertexai": True, "project": "p", "location": "europe-west1"}


def test_rejects_config_with_grounding_tools(tmp_path):
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    config["groundingTools"] = [{"googleSearch": {}}]
    bad_config_path = tmp_path / "bad-config.json"
    bad_config_path.write_text(json.dumps(config), encoding="utf-8")
    with pytest.raises(VertexBackendConfigurationError):
        VertexBackend(
            bad_config_path,
            project_id="p",
            client_factory=lambda **_: _FakeClient(_fake_response()),
        )


def test_rejects_config_missing_response_schema(tmp_path):
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    del config["generationConfig"]["responseSchema"]
    bad_config_path = tmp_path / "bad-config.json"
    bad_config_path.write_text(json.dumps(config), encoding="utf-8")
    with pytest.raises(VertexBackendConfigurationError):
        VertexBackend(
            bad_config_path,
            project_id="p",
            client_factory=lambda **_: _FakeClient(_fake_response()),
        )
