"""TRAIN-02 command-line dataset validator."""

import argparse
import json
import sys
from pathlib import Path
from typing import Optional, Sequence

from vis_training.validation import DatasetConfigurationError, ValidationReport, validate_dataset
from vis_training.validation import codes
from vis_training.validation.models import Diagnostic


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate a VIS TRAIN JSONL dataset.")
    parser.add_argument("--dataset", required=True, type=Path)
    parser.add_argument("--input-schema", required=True, type=Path)
    parser.add_argument("--output-schema", required=True, type=Path)
    parser.add_argument("--format", choices=("text", "json"), default="text")
    parser.add_argument("--output", type=Path)
    return parser


def _render(report: ValidationReport, output_format: str) -> str:
    if output_format == "json":
        return json.dumps(report.to_dict(), indent=2, sort_keys=False) + "\n"
    return report.render_text()


def _write(content: str, output_path: Optional[Path]) -> None:
    if output_path is None:
        sys.stdout.write(content)
        return
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(content, encoding="utf-8")


def _configuration_report(error: DatasetConfigurationError) -> ValidationReport:
    report = ValidationReport(dataset=error.path)
    report.diagnostics.append(
        Diagnostic(None, None, error.code, error.path, "error", error.message)
    )
    return report


def _internal_report() -> ValidationReport:
    report = ValidationReport(dataset="unknown")
    report.diagnostics.append(
        Diagnostic(None, None, codes.INTERNAL_ERROR, "$", "error", "Unexpected internal validation error.")
    )
    return report


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = _parser().parse_args(argv)
    try:
        report = validate_dataset(args.dataset, args.input_schema, args.output_schema)
        exit_code = 1 if report.errors else 0
    except DatasetConfigurationError as error:
        report = _configuration_report(error)
        exit_code = 2
    except Exception:
        report = _internal_report()
        exit_code = 3
    try:
        _write(_render(report, args.format), args.output)
    except OSError:
        output_error = DatasetConfigurationError(
            codes.OUTPUT_IO_ERROR,
            str(args.output),
            "Report output could not be written.",
        )
        sys.stdout.write(_render(_configuration_report(output_error), args.format))
        return 2
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
