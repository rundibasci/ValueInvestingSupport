"""TA3 command-line entry point: build the real-ticker knowledge-leakage set."""

import argparse
import json
from pathlib import Path

from vis_training.vertex.real_ticker_dataset import write_dataset


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Build datasets/benchmark/real-ticker-knowledge-leakage-v1.jsonl"
    )
    parser.add_argument("--output", type=Path, required=True)
    return parser


def main(argv=None) -> int:
    args = _parser().parse_args(argv)
    count = write_dataset(args.output)
    print(json.dumps({"records": count, "output": str(args.output)}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
