"""Deterministic stratified smoke selection; never creates cloud resources."""

from collections import defaultdict
from pathlib import Path
from typing import Any, Dict, List

from .errors import TeacherDataError
from .io import iter_jsonl, write_json

PRIORITY = ["VALUE_TRAP", "OVERVALUED_STRONG", "DIVIDEND_RISK", "STALE_DATA", "CONTRADICTORY_SIGNALS", "ADVERSARIAL_INPUT"]


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


def write_smoke_plan(scenarios_path: Path, output_path: Path, count: int = 20) -> Dict[str, Any]:
    selected = select_smoke(scenarios_path, count)
    plan = {"formatVersion": "1.0", "createsCloudResources": False, "requiresExplicitExecutionApproval": True,
            "scenarioCount": len(selected), "candidateSlotCount": len(selected) * 2,
            "scenarios": [{"scenarioId": x["scenarioId"], "scenarioType": x["scenarioType"], "difficulty": x["difficulty"]} for x in selected]}
    write_json(output_path, plan)
    return plan
