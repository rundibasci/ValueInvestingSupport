"""Aggregate candidate and critic accounting with explicit denominators."""

from collections import Counter, defaultdict
from pathlib import Path
from statistics import mean
from typing import Any, Dict, Optional

from .io import iter_jsonl, write_json


def build_report(candidates_path: Path, critics_path: Optional[Path] = None, *, hourly_rate: Optional[float] = None) -> Dict[str, Any]:
    candidates = list(iter_jsonl(candidates_path))
    critics = list(iter_jsonl(critics_path)) if critics_path and Path(critics_path).exists() else []
    statuses, categories = Counter(), defaultdict(Counter)
    for item in candidates:
        statuses[item["status"]] += 1
        categories[item["scenarioType"]][item["status"]] += 1
    reviewed = [item for item in critics if item["status"] == "REVIEWED"]
    verdicts = Counter(item["parsedReview"]["verdict"] for item in reviewed)
    latencies = [item["latencyMs"] for item in candidates + critics if item.get("latencyMs") is not None]
    total_latency_ms = sum(latencies)
    report = {
        "formatVersion": "1.0", "source": "SYNTHETIC_TEACHER", "automaticTrainingPromotion": False,
        "denominators": {"candidateSlots": len(candidates), "parseableCandidates": sum(x.get("parsedOutput") is not None for x in candidates),
                         "criticEligibleCandidates": sum(bool(x.get("criticEligible")) for x in candidates), "criticReviews": len(critics)},
        "candidateStatuses": dict(sorted(statuses.items())),
        "critic": {"statuses": dict(sorted(Counter(x["status"] for x in critics).items())), "verdicts": dict(sorted(verdicts.items()))},
        "byScenarioType": {key: dict(sorted(value.items())) for key, value in sorted(categories.items())},
        "usage": {"inputTokens": sum(x.get("inputTokens") or 0 for x in candidates + critics),
                  "outputTokens": sum(x.get("outputTokens") or 0 for x in candidates + critics),
                  "averageLatencyMs": round(mean(latencies), 3) if latencies else None,
                  "estimatedComputeHoursFromLatency": round(total_latency_ms / 3_600_000, 6),
                  "hourlyRateUsd": hourly_rate,
                  "estimatedCostUsd": round(total_latency_ms / 3_600_000 * hourly_rate, 4) if hourly_rate is not None else None},
    }
    return report


def write_report(candidates_path: Path, critics_path: Optional[Path], output_path: Path, *, hourly_rate: Optional[float] = None) -> Dict[str, Any]:
    report = build_report(candidates_path, critics_path, hourly_rate=hourly_rate)
    write_json(output_path, report)
    return report
