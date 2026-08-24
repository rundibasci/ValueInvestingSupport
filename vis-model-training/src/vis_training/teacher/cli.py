"""Offline-first TRAIN-05 command-line tooling."""

import argparse
import json
import sys
from pathlib import Path

from .config import load_local_config, readiness
from .critic import CriticRunner
from .errors import TeacherToolingError
from .fake_backend import FakeCriticBackend, FakeTeacherBackend
from .huggingface_backend import HuggingFaceBackend
from .pipeline import CandidateRunner
from .report import write_report
from .recovery import recover_wrapped_critic
from .review import check_review, prepare_review, summarize_review
from .smoke import write_calibration_plan, write_smoke_plan
from .calibration_gate import evaluate_files


def _parser():
    parser = argparse.ArgumentParser(prog="vis-teacher", description="TRAIN-05 local tooling; never provisions cloud resources")
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--config", type=Path, default=Path("config/teacher-v2.json"))
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("validate-config")
    generate = commands.add_parser("local-generate"); generate.add_argument("--scenarios", type=Path, required=True); generate.add_argument("--output", type=Path, required=True); generate.add_argument("--manifest", type=Path, required=True); generate.add_argument("--limit", type=int)
    real_generate = commands.add_parser("runpod-generate"); real_generate.add_argument("--scenarios", type=Path, required=True); real_generate.add_argument("--output", type=Path, required=True); real_generate.add_argument("--manifest", type=Path, required=True); real_generate.add_argument("--limit", type=int); real_generate.add_argument("--run-id", default="train-05-smoke")
    critic = commands.add_parser("local-critic"); critic.add_argument("--scenarios", type=Path, required=True); critic.add_argument("--candidates", type=Path, required=True); critic.add_argument("--output", type=Path, required=True)
    real_critic = commands.add_parser("runpod-critic"); real_critic.add_argument("--scenarios", type=Path, required=True); real_critic.add_argument("--candidates", type=Path, required=True); real_critic.add_argument("--output", type=Path, required=True); real_critic.add_argument("--run-id", default="train-05-calibration-v2-critic-v3")
    report = commands.add_parser("report"); report.add_argument("--candidates", type=Path, required=True); report.add_argument("--critics", type=Path); report.add_argument("--output", type=Path, required=True); report.add_argument("--hourly-rate", type=float)
    smoke = commands.add_parser("smoke-plan"); smoke.add_argument("--scenarios", type=Path, required=True); smoke.add_argument("--output", type=Path, required=True); smoke.add_argument("--dataset-output", type=Path); smoke.add_argument("--count", type=int, default=20)
    calibration = commands.add_parser("calibration-plan"); calibration.add_argument("--scenarios", type=Path, required=True); calibration.add_argument("--output", type=Path, required=True); calibration.add_argument("--dataset-output", type=Path); calibration.add_argument("--count", type=int, default=50); calibration.add_argument("--program-budget-cap-usd", type=float, default=50.0); calibration.add_argument("--calibration-budget-cap-usd", type=float, default=10.0)
    review = commands.add_parser("prepare-review"); review.add_argument("--candidates", type=Path, required=True); review.add_argument("--output", type=Path, required=True); review.add_argument("--minimum", type=int, default=30)
    check = commands.add_parser("check-review"); check.add_argument("--review", type=Path, required=True)
    summary = commands.add_parser("summarize-review"); summary.add_argument("--review", type=Path, required=True); summary.add_argument("--output", type=Path, required=True)
    recover = commands.add_parser("recover-critic"); recover.add_argument("--input", type=Path, required=True); recover.add_argument("--output", type=Path, required=True); recover.add_argument("--schema", type=Path, default=Path("schemas/critic-review.schema.json"))
    gate = commands.add_parser("calibration-gate"); gate.add_argument("--report", type=Path, required=True); gate.add_argument("--human-summary", type=Path, required=True); gate.add_argument("--thresholds", type=Path, default=Path("config/calibration-v2-gate.json")); gate.add_argument("--output", type=Path)
    return parser


def main(argv=None):
    args = _parser().parse_args(argv)
    try:
        if args.command == "validate-config": result = readiness(args.root, args.config)
        elif args.command == "local-generate": result = CandidateRunner(args.root, args.config, FakeTeacherBackend()).run(args.scenarios, args.output, args.manifest, limit=args.limit)
        elif args.command == "runpod-generate":
            config = readiness(args.root, args.config)
            if not config["smokeReady"]: raise TeacherToolingError("RunPod inference blocked by readiness gate")
            raw = load_local_config(args.root, args.config)["config"]
            backend = HuggingFaceBackend(raw["teacher"]["modelId"], raw["teacher"]["modelRevision"], raw["teacher"]["tokenizerRevision"])
            result = CandidateRunner(args.root, args.config, backend).run(args.scenarios, args.output, args.manifest, run_id=args.run_id, hardware_profile="RUNPOD_SECURE_CLOUD", limit=args.limit)
        elif args.command == "local-critic": result = CriticRunner(args.root, args.config, FakeCriticBackend()).run(args.scenarios, args.candidates, args.output)
        elif args.command == "runpod-critic":
            raw = load_local_config(args.root, args.config)["config"]
            backend = HuggingFaceBackend(raw["critic"]["modelId"], raw["critic"]["modelRevision"], raw["teacher"]["tokenizerRevision"])
            result = CriticRunner(args.root, args.config, backend, run_id=args.run_id).run(args.scenarios, args.candidates, args.output)
        elif args.command == "report": result = write_report(args.candidates, args.critics, args.output, hourly_rate=args.hourly_rate)
        elif args.command == "smoke-plan": result = write_smoke_plan(args.scenarios, args.output, args.count, dataset_output=args.dataset_output)
        elif args.command == "calibration-plan": result = write_calibration_plan(args.scenarios, args.output, args.count, dataset_output=args.dataset_output, program_budget_cap_usd=args.program_budget_cap_usd, calibration_budget_cap_usd=args.calibration_budget_cap_usd)
        elif args.command == "prepare-review": result = prepare_review(args.candidates, args.output, args.minimum)
        elif args.command == "check-review":
            result = check_review(args.review)
            print(json.dumps(result, sort_keys=True)); return 0 if result["complete"] else 3
        elif args.command == "summarize-review": result = summarize_review(args.review, args.output)
        elif args.command == "recover-critic": result = recover_wrapped_critic(args.input, args.output, args.schema if args.schema.is_absolute() else args.root / args.schema)
        elif args.command == "calibration-gate":
            resolve = lambda path: path if path.is_absolute() else args.root / path
            result = evaluate_files(resolve(args.report), resolve(args.human_summary), resolve(args.thresholds), resolve(args.output) if args.output else None)
            print(json.dumps(result, sort_keys=True)); return 0 if result["decision"] == "GO" else 4
        print(json.dumps(result, sort_keys=True)); return 0
    except TeacherToolingError as error:
        print(json.dumps({"error": type(error).__name__, "message": str(error)}, sort_keys=True), file=sys.stderr); return error.exit_code
    except Exception as error:
        print(json.dumps({"error": type(error).__name__, "message": "Unexpected internal TRAIN-05 tooling error."}, sort_keys=True), file=sys.stderr); return 5


if __name__ == "__main__":
    raise SystemExit(main())
