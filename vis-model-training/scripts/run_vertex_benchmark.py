"""TA3 live benchmark run driver — Vertex AI Gemini, real spend.

Runs BenchmarkRunner (TRAIN-03's unchanged harness) against VertexBackend
for all three datasets the roadmap's TA3 capability-gate run requires:
TRAIN-03's base-benchmark-v1 (50), TRAIN-04's scenarios-v1 (500), and TA3's
own real-ticker-knowledge-leakage-v1 (24). Idempotent/resumable per dataset
(BenchmarkRunner.run skips exampleIds already present in the output file),
so an interrupted run can simply be re-invoked.

Deliberately a standalone script rather than an extension of cli.py's `run`
subcommand: that subcommand's --manifest/verify_freeze_manifest machinery is
HuggingFaceBackend/local-checkpoint-specific (freezing prompt/config file
hashes against a local model revision). VertexBackend has no local
checkpoint to freeze; this script writes its own manifest instead, in the
same shape, without forking BenchmarkRunner/metrics.py/review.py.

Usage: PYTHONPATH=src .venv/bin/python3 scripts/run_vertex_benchmark.py \
    [--output-dir NAME] [--base-benchmark-dataset PATH]

--output-dir defaults to "vertex-gemini-2.5-flash-v1" (TA3's original run). TA4's Group 7
prompt-fix corpus re-run uses --output-dir vertex-gemini-2.5-flash-v3 so TA3's v1 results
are never overwritten (mission.md Principle 6, immutable history) -- previously a separate
near-duplicate script (run_vertex_benchmark_v3.py); consolidated here once the v3 run this
argument was added for had already started against that duplicate (see git history).

--base-benchmark-dataset defaults to datasets/benchmark/base-benchmark-v1.jsonl (TRAIN-03's
canonical, prompt-v2-pinned 50 cases -- protected by
tests/benchmark/test_train03_benchmark.py's drift check, never edited in place). A prompt-
version re-run (e.g. Group 7's v3 corpus re-run) must pass the matching variant file (e.g.
base-benchmark-v1-promptv3.jsonl, same input/expected/metadata, only the embedded system
message differs) instead of overriding the canonical file's own committed content -- the
harness reads the system prompt from each dataset record's embedded messages[0], never from
config/vertex-gemini-v1.json's promptPath at call time (confirmed the hard way: TA4's first
v3 corpus attempt silently reused the v2 text baked into these files and had to be redone).
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from vis_training.benchmark.io import write_json  # noqa: E402
from vis_training.benchmark.runner import BenchmarkRunner  # noqa: E402
from vis_training.benchmark.vertex_backend import VertexBackend  # noqa: E402

def _default_datasets(base_benchmark_path: Path) -> list:
    return [
        ("base-benchmark-v1", base_benchmark_path),
        # scenarios-v1.jsonl (TRAIN-04's raw generator output) is not in the
        # benchmark harness's 3-message contract; scenarios-benchmark-v1.jsonl
        # (built by scripts/build_scenarios_benchmark_dataset.py) is the
        # converted, harness-compatible form actually run here.
        ("scenarios-v1", ROOT / "datasets" / "benchmark" / "scenarios-benchmark-v1.jsonl"),
        (
            "real-ticker-knowledge-leakage-v1",
            ROOT / "datasets" / "benchmark" / "real-ticker-knowledge-leakage-v1.jsonl",
        ),
    ]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", default="vertex-gemini-2.5-flash-v1")
    parser.add_argument(
        "--base-benchmark-dataset",
        type=Path,
        default=ROOT / "datasets" / "benchmark" / "base-benchmark-v1.jsonl",
    )
    args = parser.parse_args()
    OUTPUT_DIR = ROOT / "results" / args.output_dir
    DATASETS = _default_datasets(args.base_benchmark_dataset)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    backend = VertexBackend(ROOT / "config" / "vertex-gemini-v1.json")
    runner = BenchmarkRunner(backend, max_new_tokens=1024)

    write_json(
        OUTPUT_DIR / "run-manifest.json",
        {
            "formatVersion": "1.0",
            "backend": backend.manifest(),
            "decoding": {
                "doSample": False,
                "temperature": backend.temperature,
                "maxNewTokens": 1024,
                "thinkingBudget": backend.thinking_budget,
                "batchSize": 1,
            },
            "datasets": [name for name, _ in DATASETS],
            "startedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        },
    )

    totals = {}
    for name, dataset_path in DATASETS:
        output_path = OUTPUT_DIR / f"{name}.results.jsonl"
        print(f"=== {name}: starting (dataset={dataset_path}) ===", flush=True)
        summary = runner.run(dataset_path, output_path)
        totals[name] = summary
        print(f"=== {name}: {json.dumps(summary)} ===", flush=True)

    write_json(OUTPUT_DIR / "run-summary.json", totals)
    print(json.dumps(totals, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
