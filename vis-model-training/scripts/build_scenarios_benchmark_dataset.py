"""TA3 command-line entry point: convert scenarios-v1.jsonl (TRAIN-04) into
the benchmark harness's 3-message contract (datasets/benchmark/
scenarios-benchmark-v1.jsonl)."""

import argparse
import json
from pathlib import Path

from vis_training.vertex.scenarios_benchmark_dataset import write_dataset


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build datasets/benchmark/scenarios-benchmark-v1.jsonl from scenarios-v1.jsonl"
    )
    parser.add_argument("--scenarios", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv=None) -> int:
    args = _parser().parse_args(argv)
    count = write_dataset(args.scenarios, args.output)
    print(json.dumps({"records": count, "output": str(args.output)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
