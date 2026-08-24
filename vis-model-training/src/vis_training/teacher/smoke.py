"""Deterministic stratified smoke selection; never creates cloud resources."""

from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List

from .errors import TeacherDataError
from .io import canonical_json, iter_jsonl, write_json

PRIORITY = ["VALUE_TRAP", "OVERVALUED_STRONG", "DIVIDEND_RISK", "STALE_DATA", "CONTRADICTORY_SIGNALS", "ADVERSARIAL_INPUT"]
CAPABILITY_PROBE_TYPES = [
    "ADVERSARIAL_INPUT",
    "DIVIDEND_RISK",
    "FAIR_VALUE",
    "HIGH_LEVERAGE",
    "INCONSISTENT_DATA",
    "INSUFFICIENT_DATA",
    "OVERVALUED_STRONG",
    "STALE_DATA",
    "UNDERVALUED_WEAK",
    "VALUE_TRAP",
]


def select_smoke(scenarios_path: Path, count: int = 20) -> List[Dict[str, Any]]:
    groups = defaultdict(list)
    for scenario in iter_jsonl(scenarios_path):
        groups[scenario["scenarioType"]].append(scenario)
    selected = [sorted(items, key=lambda x: x["scenarioId"])[0] for _, items in sorted(groups.items())]
    used = {item["scenarioId"] for item in selected}
    for category in PRIORITY:
        for item in sorted(groups.get(category, []), key=lambda x: x["scenarioId"]):
            if len(selected) >= count:
                break
            if item["scenarioId"] not in used:
                selected.append(item); used.add(item["scenarioId"]); break
    if len(selected) != count:
        raise TeacherDataError(f"Cannot construct stratified smoke set of {count}; selected {len(selected)}")
    return selected


def select_calibration(scenarios_path: Path, count: int = 50) -> List[Dict[str, Any]]:
    """Select a deterministic, category-balanced calibration set."""
    groups = defaultdict(list)
    for scenario in iter_jsonl(scenarios_path):
        groups[scenario["scenarioType"]].append(scenario)
    if count < len(groups):
        raise TeacherDataError(
            f"Calibration count {count} cannot cover all {len(groups)} categories"
        )
    ordered_groups = {
        category: sorted(items, key=lambda item: item["scenarioId"])
        for category, items in sorted(groups.items())
    }
    selected = []
    ordinal = 0
    while len(selected) < count:
        added = False
        for items in ordered_groups.values():
            if ordinal < len(items) and len(selected) < count:
                selected.append(items[ordinal])
                added = True
        if not added:
            break
        ordinal += 1
    if len(selected) != count:
        raise TeacherDataError(
            f"Cannot construct stratified calibration set of {count}; selected {len(selected)}"
        )
    return selected


def select_capability_probe(scenarios_path: Path) -> List[Dict[str, Any]]:
    """Select ten deterministic scenarios targeting observed v2 failure modes."""
    groups = defaultdict(list)
    for scenario in iter_jsonl(scenarios_path):
        groups[scenario["scenarioType"]].append(scenario)
    missing = [name for name in CAPABILITY_PROBE_TYPES if not groups.get(name)]
    if missing:
        raise TeacherDataError("Capability probe lacks required scenario types: " + ", ".join(missing))
    return [sorted(groups[name], key=lambda item: item["scenarioId"])[0] for name in CAPABILITY_PROBE_TYPES]


def write_smoke_plan(scenarios_path: Path, output_path: Path, count: int = 20, *, dataset_output: Path = None) -> Dict[str, Any]:
    selected = select_smoke(scenarios_path, count)
    plan = {"formatVersion": "1.0", "createsCloudResources": False, "requiresExplicitExecutionApproval": True,
            "scenarioCount": len(selected), "candidateSlotCount": len(selected) * 2,
            "scenarios": [{"scenarioId": x["scenarioId"], "scenarioType": x["scenarioType"], "difficulty": x["difficulty"]} for x in selected]}
    write_json(output_path, plan)
    if dataset_output is not None:
        dataset_output = Path(dataset_output)
        dataset_output.parent.mkdir(parents=True, exist_ok=True)
        dataset_output.write_text("".join(canonical_json(item) + "\n" for item in selected), encoding="utf-8")
    return plan


def write_capability_probe_plan(scenarios_path: Path, output_path: Path, *, dataset_output: Path = None) -> Dict[str, Any]:
    selected = select_capability_probe(scenarios_path)
    plan = {
        "formatVersion": "1.0",
        "planType": "CAPABILITY_PROBE",
        "createsCloudResources": False,
        "requiresExplicitExecutionApproval": True,
        "requiresStopBeforeCalibration": True,
        "scenarioCount": len(selected),
        "candidateSlotCount": len(selected) * 2,
        "targetedFailureModes": [
            "STRICT_JSON_AND_SCHEMA",
            "DECISIVE_CRITIC",
            "INVALIDATION_DIRECTION",
            "UNSUPPORTED_QUALITATIVE_THRESHOLDS",
            "WEAK_MARGIN_OF_SAFETY",
            "HUMAN_REVIEW_CONSISTENCY",
        ],
        "scenarios": [
            {"scenarioId": item["scenarioId"], "scenarioType": item["scenarioType"], "difficulty": item["difficulty"]}
            for item in selected
        ],
    }
    write_json(output_path, plan)
    if dataset_output is not None:
        dataset_output = Path(dataset_output)
        dataset_output.parent.mkdir(parents=True, exist_ok=True)
        dataset_output.write_text("".join(canonical_json(item) + "\n" for item in selected), encoding="utf-8")
    return plan


def write_calibration_plan(scenarios_path: Path, output_path: Path, count: int = 50, *, dataset_output: Path = None,
                           program_budget_cap_usd: float = 50.0, calibration_budget_cap_usd: float = 10.0) -> Dict[str, Any]:
    if program_budget_cap_usd <= 0 or calibration_budget_cap_usd <= 0:
        raise TeacherDataError("Budget caps must be positive")
    if calibration_budget_cap_usd > program_budget_cap_usd:
        raise TeacherDataError("Calibration budget cap cannot exceed program budget cap")
    selected = select_calibration(scenarios_path, count)
    plan = {"formatVersion": "1.0", "planType": "CALIBRATION", "createsCloudResources": False,
            "requiresExplicitExecutionApproval": True, "requiresStopBeforeBulk": True,
            "programBudgetCapUsd": program_budget_cap_usd,
            "calibrationBudgetCapUsd": calibration_budget_cap_usd,
            "scenarioCount": len(selected), "candidateSlotCount": len(selected) * 2,
            "scenarios": [{"scenarioId": x["scenarioId"], "scenarioType": x["scenarioType"], "difficulty": x["difficulty"]} for x in selected]}
    write_json(output_path, plan)
    if dataset_output is not None:
        dataset_output = Path(dataset_output)
        dataset_output.parent.mkdir(parents=True, exist_ok=True)
        dataset_output.write_text("".join(canonical_json(item) + "\n" for item in selected), encoding="utf-8")
    return plan
