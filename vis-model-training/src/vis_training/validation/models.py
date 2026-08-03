"""Versioned report models for the TRAIN dataset validator."""

from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional


@dataclass(frozen=True)
class Diagnostic:
    line: Optional[int]
    example_id: Optional[str]
    code: str
    path: str
    severity: str
    message: str

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["exampleId"] = data.pop("example_id")
        return data


@dataclass
class ValidationReport:
    dataset: str
    records: int = 0
    valid: int = 0
    invalid: int = 0
    diagnostics: List[Diagnostic] = field(default_factory=list)
    format_version: str = "1.0"

    @property
    def warnings(self) -> int:
        return sum(item.severity == "warning" for item in self.diagnostics)

    @property
    def errors(self) -> int:
        return sum(item.severity == "error" for item in self.diagnostics)

    def sorted_diagnostics(self) -> List[Diagnostic]:
        return sorted(
            self.diagnostics,
            key=lambda item: (
                item.line if item.line is not None else -1,
                item.path,
                item.code,
            ),
        )

    def to_dict(self) -> Dict[str, Any]:
        return {
            "formatVersion": self.format_version,
            "dataset": self.dataset,
            "records": self.records,
            "valid": self.valid,
            "invalid": self.invalid,
            "warnings": self.warnings,
            "errors": self.errors,
            "diagnostics": [item.to_dict() for item in self.sorted_diagnostics()],
        }

    def render_text(self) -> str:
        lines = [
            f"dataset: {self.dataset}",
            f"records: {self.records}",
            f"valid: {self.valid}",
            f"invalid: {self.invalid}",
            f"warnings: {self.warnings}",
            f"errors: {self.errors}",
        ]
        for item in self.sorted_diagnostics():
            location = f"line {item.line}" if item.line is not None else "dataset"
            example = f" exampleId={item.example_id}" if item.example_id else ""
            lines.append(
                f"[{item.severity}] {item.code} {location}{example} {item.path}: {item.message}"
            )
        return "\n".join(lines) + "\n"
