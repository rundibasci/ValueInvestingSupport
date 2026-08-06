"""TRAIN-04 scenario generator command-line interface."""

import argparse
import json
import sys
from pathlib import Path

from .errors import ScenarioConfigurationError, ScenarioContaminationError, ScenarioError, ScenarioValidationError
from .generator import generate_scenarios
from .io import dataset_bytes, load_jsonl, load_object, write_artifacts_atomic
from .validator import validate_dataset_paths, validate_records

DEFAULT_CONFIG = Path("config/scenarios-v1.json")
DEFAULT_CATALOG = Path("config/scenario-catalog-v1.json")
DEFAULT_SCHEMA = Path("schemas/thesis-input.schema.json")
DEFAULT_PRIOR = (Path("datasets/seed-dataset-v1.jsonl"), Path("datasets/benchmark/base-benchmark-v1.jsonl"))


def _shared(parser):
    parser.add_argument("--config", type=Path, default=DEFAULT_CONFIG)
    parser.add_argument("--catalog", type=Path, default=DEFAULT_CATALOG)
    parser.add_argument("--input-schema", type=Path, default=DEFAULT_SCHEMA)
    parser.add_argument("--seed-dataset", type=Path, default=DEFAULT_PRIOR[0])
    parser.add_argument("--benchmark-dataset", type=Path, default=DEFAULT_PRIOR[1])


def _parser():
    parser = argparse.ArgumentParser(prog="vis-scenarios")
    commands = parser.add_subparsers(dest="command", required=True)
    generate = commands.add_parser("generate")
    _shared(generate)
    generate.add_argument("--seed", type=int, default=20260806)
    generate.add_argument("--count", type=int, default=500)
    generate.add_argument("--output", type=Path, required=True)
    generate.add_argument("--report", type=Path, required=True)
    validate = commands.add_parser("validate")
    _shared(validate)
    validate.add_argument("--dataset", type=Path, required=True)
    validate.add_argument("--expected-count", type=int, default=500)
    reproducible = commands.add_parser("verify-reproducibility")
    _shared(reproducible)
    reproducible.add_argument("--seed", type=int, default=20260806)
    reproducible.add_argument("--count", type=int, default=500)
    return parser


def _inputs(args):
    config = load_object(args.config)
    catalog = load_object(args.catalog)
    schema = load_object(args.input_schema)
    prior = (args.seed_dataset, args.benchmark_dataset)
    return config, catalog, schema, prior


def _generate_checked(args):
    config, catalog, schema, prior = _inputs(args)
    records, report = generate_scenarios(config, catalog, seed=args.seed, count=args.count)
    failures, contamination = validate_records(records, input_schema=schema, catalog=catalog, expected_count=args.count, contamination_paths=prior)
    if failures:
        raise ScenarioValidationError(failures)
    if contamination:
        raise ScenarioContaminationError(contamination)
    return records, report


def main(argv=None):
    args = _parser().parse_args(argv)
    try:
        if args.command == "generate":
            records, report = _generate_checked(args)
            content = dataset_bytes(records)
            write_artifacts_atomic(args.output, args.report, content, report)
            print(json.dumps({"status": "generated", "records": len(records), "datasetSha256": report["datasetSha256"]}, sort_keys=True))
        elif args.command == "validate":
            failures, contamination = validate_dataset_paths(args.dataset, args.input_schema, args.catalog, (args.seed_dataset, args.benchmark_dataset), args.expected_count)
            if failures:
                raise ScenarioValidationError(failures)
            if contamination:
                raise ScenarioContaminationError(contamination)
            print(json.dumps({"status": "valid", "records": len(load_jsonl(args.dataset))}, sort_keys=True))
        elif args.command == "verify-reproducibility":
            first_records, first_report = _generate_checked(args)
            second_records, second_report = _generate_checked(args)
            if dataset_bytes(first_records) != dataset_bytes(second_records) or first_report != second_report:
                raise ScenarioValidationError(["repeated generation is not byte-identical"])
            print(json.dumps({"status": "reproducible", "records": len(first_records), "datasetSha256": first_report["datasetSha256"]}, sort_keys=True))
        return 0
    except ScenarioValidationError as error:
        print(json.dumps({"error": type(error).__name__, "failures": error.failures}, sort_keys=True), file=sys.stderr)
        return error.exit_code
    except ScenarioContaminationError as error:
        print(json.dumps({"error": type(error).__name__, "failures": error.failures}, sort_keys=True), file=sys.stderr)
        return error.exit_code
    except ScenarioError as error:
        print(json.dumps({"error": type(error).__name__, "message": str(error)}, sort_keys=True), file=sys.stderr)
        return error.exit_code
    except Exception as error:
        print(json.dumps({"error": type(error).__name__, "message": "Unexpected internal scenario generator error."}, sort_keys=True), file=sys.stderr)
        return 5


if __name__ == "__main__":
    raise SystemExit(main())
