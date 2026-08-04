"""Command-line entry point for local and RunPod TRAIN-03 operations."""

import argparse
import json
import os
import sys
from pathlib import Path

from .catalog import write_catalog
from .freeze import build_freeze_manifest, verify_freeze_manifest
from .huggingface_backend import HuggingFaceBackend
from .io import write_json
from .metrics import compute_metrics
from .review import prepare_review_form, validate_completed_review
from .runner import BenchmarkRunner


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="vis-benchmark")
    subparsers = parser.add_subparsers(dest="command", required=True)
    catalog = subparsers.add_parser("build-catalog")
    catalog.add_argument("--output", type=Path, required=True)
    catalog.add_argument("--prompt", type=Path, required=True)

    freeze = subparsers.add_parser("freeze")
    freeze.add_argument("--root", type=Path, required=True)
    freeze.add_argument("--output", type=Path, required=True)
    freeze.add_argument("files", nargs="+", type=Path)

    verify = subparsers.add_parser("verify-freeze")
    verify.add_argument("--root", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)

    run = subparsers.add_parser("run")
    run.add_argument("--dataset", type=Path, required=True)
    run.add_argument("--output", type=Path, required=True)
    run.add_argument("--manifest", type=Path, required=True)
    run.add_argument("--model", default="google/gemma-3-4b-it")
    run.add_argument("--revision", default=os.environ.get("GEMMA_MODEL_REVISION"))
    run.add_argument("--limit", type=int)

    metrics = subparsers.add_parser("metrics")
    metrics.add_argument("--results", type=Path, required=True)
    metrics.add_argument("--dataset", type=Path, required=True)
    metrics.add_argument("--output-schema", type=Path, required=True)
    metrics.add_argument("--output", type=Path, required=True)

    review = subparsers.add_parser("prepare-review")
    review.add_argument("--results", type=Path, required=True)
    review.add_argument("--output", type=Path, required=True)
    review.add_argument("--minimum", type=int, default=20)

    check_review = subparsers.add_parser("check-review")
    check_review.add_argument("--review", type=Path, required=True)
    return parser


def main(argv=None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "build-catalog":
            count = write_catalog(args.output, args.prompt)
            print(json.dumps({"records": count, "output": str(args.output)}))
        elif args.command == "freeze":
            build_freeze_manifest(args.files, root=args.root, output=args.output)
            print(json.dumps({"status": "frozen", "manifest": str(args.output)}))
        elif args.command == "verify-freeze":
            failures = verify_freeze_manifest(args.manifest, root=args.root)
            print(json.dumps({"status": "valid" if not failures else "invalid", "failures": failures}))
            return 0 if not failures else 1
        elif args.command == "run":
            failures = verify_freeze_manifest(args.manifest, root=Path.cwd())
            if failures:
                print(json.dumps({"error": "FREEZE_MISMATCH", "failures": failures}))
                return 2
            backend = HuggingFaceBackend(args.model, args.revision)
            manifest_path = args.output.with_suffix(".manifest.json")
            write_json(
                manifest_path,
                {
                    "formatVersion": "1.0",
                    "backend": backend.manifest(),
                    "decoding": {"doSample": False, "temperature": 0.0, "maxNewTokens": 1024, "batchSize": 1},
                },
            )
            summary = BenchmarkRunner(backend).run(args.dataset, args.output, limit=args.limit)
            print(json.dumps(summary, sort_keys=True))
        elif args.command == "metrics":
            report = compute_metrics(args.results, args.dataset, args.output_schema, output_path=args.output)
            print(json.dumps(report["global"], sort_keys=True))
        elif args.command == "prepare-review":
            form = prepare_review_form(args.results, args.output, minimum=args.minimum)
            print(json.dumps({"reviews": len(form["reviews"]), "output": str(args.output)}))
        elif args.command == "check-review":
            review = json.loads(args.review.read_text(encoding="utf-8"))
            failures = validate_completed_review(review)
            print(json.dumps({"status": "valid" if not failures else "invalid", "failures": failures}))
            return 0 if not failures else 1
        return 0
    except Exception as error:
        print(json.dumps({"error": type(error).__name__, "message": str(error)}), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
