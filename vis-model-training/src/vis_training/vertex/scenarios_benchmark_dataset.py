"""Convert TRAIN-04's raw scenarios-v1.jsonl into the benchmark harness's
3-message (system/user/assistant) contract, so TRAIN-04's 500 scenarios can
run through the same `BenchmarkRunner`/`VertexBackend` pipeline TA3 already
uses for TRAIN-03's base-benchmark-v1 and TA3's own real-ticker set — one
Vertex AI call per scenario, matching ADR-002/TA1's cost estimate ("TRAIN-04
(500 scenarios) ... 550 total Gemini calls" — 1:1, not multiplied per
scenario the way TRAIN-05's closed candidate-sampling teacher pipeline did).

`scenarios-v1.jsonl` (built by `vis_training/scenarios/generator.py` for
TRAIN-04/TRAIN-05's candidate-generation pipeline) has no `messages`/
`metadata` envelope and no precomputed `expected` — each record is just
{scenarioId, scenarioType, variantId, difficulty, seed, generatorVersion,
input}. `input` already matches `thesis-input.schema.json`'s field set
(confirmed against `real_ticker_dataset.py`'s `_BASE_INPUT_DEFAULTS`), so
`expected` is derived with the exact same generic, grounded-only rules TA3's
real-ticker dataset already uses (`expected_thesis.derive_expected_thesis`)
— never scenario-generator-internal knowledge the model was never given.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, Iterator

from .expected_thesis import derive_expected_thesis

DATASET_FORMAT_VERSION = "1.0"

_REPO_ROOT = Path(__file__).resolve().parents[3]
SYSTEM_PROMPT_PATH = _REPO_ROOT / "prompts" / "system-prompt-v3.txt"


def _iter_scenarios(scenarios_path: Path) -> Iterator[Dict[str, Any]]:
    with open(scenarios_path, encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if line:
                yield json.loads(line)


def convert_record(scenario: Dict[str, Any], system_prompt: str) -> Dict[str, Any]:
    input_data = scenario["input"]
    expected = derive_expected_thesis(input_data)
    return {
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": json.dumps(input_data, sort_keys=True)},
            {"role": "assistant", "content": json.dumps(expected, sort_keys=True)},
        ],
        "metadata": {
            "benchmarkCategory": scenario["scenarioType"],
            "scenarioType": scenario["scenarioType"],
            "datasetVersion": DATASET_FORMAT_VERSION,
            "exampleId": scenario["scenarioId"],
            "source": "TRAIN04_SCENARIO_CONVERTED",
            "sourceScenario": {
                "variantId": scenario.get("variantId"),
                "difficulty": scenario.get("difficulty"),
                "generatorVersion": scenario.get("generatorVersion"),
            },
        },
    }


def build_dataset(scenarios_path: Path) -> list:
    system_prompt = SYSTEM_PROMPT_PATH.read_text(encoding="utf-8")
    return [convert_record(scenario, system_prompt) for scenario in _iter_scenarios(scenarios_path)]


def write_dataset(scenarios_path: Path, output_path: Path) -> int:
    records = build_dataset(scenarios_path)
    with open(output_path, "w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True) + "\n")
    return len(records)
