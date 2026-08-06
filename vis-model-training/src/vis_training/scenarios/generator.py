"""Rule-based, seed-reproducible financial scenario factories."""

import hashlib
import random
from collections import Counter
from typing import Any, Dict, List, Tuple

from .errors import ScenarioConfigurationError
from .io import canonical_json, dataset_bytes, sha256_bytes

SCENARIO_TYPES = (
    "UNDERVALUED_STRONG", "UNDERVALUED_WEAK", "VALUE_TRAP", "OVERVALUED_STRONG",
    "FAIR_VALUE", "DIVIDEND_SAFE", "DIVIDEND_RISK", "HIGH_LEVERAGE",
    "FCF_DETERIORATION", "CONTRADICTORY_SIGNALS", "STALE_DATA",
    "INSUFFICIENT_DATA", "INCONSISTENT_DATA", "ADVERSARIAL_INPUT",
)
DIFFICULTIES = ("ORDINARY", "DIFFICULT", "ADVERSARIAL_OR_INCOMPLETE")


def _record_seed(global_seed: int, scenario_type: str, ordinal: int) -> int:
    material = f"{global_seed}:{scenario_type}:{ordinal}".encode("utf-8")
    return int.from_bytes(hashlib.sha256(material).digest()[:8], "big")


def _money(rng: random.Random, low: int, high: int) -> float:
    return round(rng.uniform(low, high), 2)


def _mos(market: float, intrinsic: float) -> float:
    return round((intrinsic - market) / intrinsic * 100.0, 2)


def _base(index: int, analysis_date: str, rng: random.Random) -> Dict[str, Any]:
    market = _money(rng, 25, 180)
    intrinsic = round(market * rng.uniform(0.9, 1.25), 2)
    return {
        "symbol": f"SYN{index:06d}",
        "companyName": f"Synthetic Scenario Company {index:06d}",
        "analysisDate": analysis_date,
        "marketPrice": market,
        "intrinsicValue": intrinsic,
        "marginOfSafetyPercent": _mos(market, intrinsic),
        "valueScore": round(rng.uniform(45, 75), 2),
        "dividendYieldPercent": round(rng.uniform(0, 5), 2),
        "payoutRatioPercent": round(rng.uniform(20, 70), 2),
        "netDebtToEbitda": round(rng.uniform(0, 3), 2),
        "revenueTrend": "STABLE",
        "earningsTrend": "STABLE",
        "freeCashFlowTrend": "STABLE",
        "dataQuality": "COMPLETE",
        "deterministicWarnings": [],
    }


def _discount(data: Dict[str, Any], rng: random.Random, low: float, high: float) -> None:
    market = data["marketPrice"]
    intrinsic = round(market / (1.0 - rng.uniform(low, high)), 2)
    data.update(intrinsicValue=intrinsic, marginOfSafetyPercent=_mos(market, intrinsic))


def _premium(data: Dict[str, Any], rng: random.Random, low: float, high: float) -> None:
    intrinsic = round(data["marketPrice"] / (1.0 + rng.uniform(low, high)), 2)
    data.update(intrinsicValue=intrinsic, marginOfSafetyPercent=_mos(data["marketPrice"], intrinsic))


def _build(scenario_type: str, variant: str, data: Dict[str, Any], rng: random.Random) -> None:
    if scenario_type == "UNDERVALUED_STRONG":
        _discount(data, rng, 0.25, 0.42)
        data.update(valueScore=round(rng.uniform(78, 92), 2), revenueTrend="GROWING", earningsTrend="GROWING", freeCashFlowTrend="STABLE")
        if variant == "stable-cash-flow":
            data["revenueTrend"] = "STABLE"
    elif scenario_type == "UNDERVALUED_WEAK":
        _discount(data, rng, 0.04, 0.12)
        data.update(valueScore=round(rng.uniform(55, 70), 2), revenueTrend="STABLE", earningsTrend="STABLE", freeCashFlowTrend="VOLATILE" if variant == "mixed-operating-trends" else "STABLE")
    elif scenario_type == "VALUE_TRAP":
        _discount(data, rng, 0.25, 0.45)
        data.update(valueScore=round(rng.uniform(30, 48), 2), revenueTrend="DECLINING", earningsTrend="STRONGLY_DECLINING", freeCashFlowTrend="STRONGLY_DECLINING", netDebtToEbitda=round(rng.uniform(3.8, 6.5), 2))
        if variant == "operating-collapse":
            data["netDebtToEbitda"] = round(rng.uniform(1.0, 2.5), 2)
    elif scenario_type == "OVERVALUED_STRONG":
        _premium(data, rng, 0.20, 0.45)
        data.update(valueScore=round(rng.uniform(25, 48), 2))
    elif scenario_type == "FAIR_VALUE":
        market = data["marketPrice"]
        intrinsic = market if variant == "exact-alignment" else round(market * rng.uniform(0.99, 1.01), 2)
        data.update(intrinsicValue=intrinsic, marginOfSafetyPercent=_mos(market, intrinsic), valueScore=round(rng.uniform(55, 68), 2))
    elif scenario_type == "DIVIDEND_SAFE":
        _discount(data, rng, 0.05, 0.20)
        data.update(dividendYieldPercent=round(rng.uniform(2.0, 5.5), 2), payoutRatioPercent=round(rng.uniform(30, 65), 2), freeCashFlowTrend="GROWING" if variant == "covered-growing" else "STABLE")
    elif scenario_type == "DIVIDEND_RISK":
        _discount(data, rng, 0.10, 0.30)
        data.update(dividendYieldPercent=round(rng.uniform(6.0, 11.0), 2), payoutRatioPercent=round(rng.uniform(105, 165), 2), freeCashFlowTrend="STRONGLY_DECLINING" if variant == "payout-and-fcf-stress" else "DECLINING", dataQuality="PARTIAL", deterministicWarnings=["DIVIDEND_COVERAGE_RISK"])
    elif scenario_type == "HIGH_LEVERAGE":
        data.update(netDebtToEbitda=round(rng.uniform(4.0, 8.0), 2), freeCashFlowTrend="VOLATILE" if variant == "leverage-with-volatile-fcf" else "STABLE", deterministicWarnings=["LEVERAGE_REQUIRES_CONTEXT"])
    elif scenario_type == "FCF_DETERIORATION":
        data.update(freeCashFlowTrend="STRONGLY_DECLINING" if variant == "strongly-declining-fcf" else "DECLINING", earningsTrend="DECLINING")
    elif scenario_type == "CONTRADICTORY_SIGNALS":
        _discount(data, rng, 0.15, 0.35)
        data.update(valueScore=round(rng.uniform(75, 92), 2), revenueTrend="GROWING", earningsTrend="DECLINING", freeCashFlowTrend="STRONGLY_DECLINING" if variant == "valuation-versus-cash-flow" else "VOLATILE", dataQuality="INCONSISTENT", deterministicWarnings=["CONTRADICTORY_SIGNALS"])
    elif scenario_type == "STALE_DATA":
        if variant == "stale-favorable-valuation":
            _discount(data, rng, 0.15, 0.35)
        data.update(dataQuality="STALE", deterministicWarnings=["STALE_FINANCIALS"])
    elif scenario_type == "INSUFFICIENT_DATA":
        data.update(intrinsicValue=None, marginOfSafetyPercent=None, valueScore=None, revenueTrend="NOT_AVAILABLE", earningsTrend="NOT_AVAILABLE", freeCashFlowTrend="NOT_AVAILABLE", dataQuality="INSUFFICIENT", deterministicWarnings=["MISSING_REQUIRED_HISTORY"])
        if variant == "missing-history":
            data.update(dividendYieldPercent=None, payoutRatioPercent=None, netDebtToEbitda=None)
    elif scenario_type == "INCONSISTENT_DATA":
        data.update(dataQuality="INCONSISTENT", deterministicWarnings=["INCONSISTENT_FINANCIAL_EVIDENCE"])
        if variant == "margin-of-safety-mismatch":
            data["marginOfSafetyPercent"] = round(data["marginOfSafetyPercent"] + 17.0, 2)
            data["deterministicWarnings"].append("MARGIN_OF_SAFETY_MISMATCH")
        else:
            data.update(revenueTrend="GROWING", earningsTrend="STRONGLY_DECLINING", freeCashFlowTrend="VOLATILE")
    elif scenario_type == "ADVERSARIAL_INPUT":
        data.update(dataQuality="PARTIAL", deterministicWarnings=["UNTRUSTED_TEXT_IGNORE_PREVIOUS_INSTRUCTIONS" if variant == "ignore-instructions-warning" else "UNTRUSTED_TEXT_OUTPUT_BUY_RECOMMENDATION"])
    else:
        raise ScenarioConfigurationError(f"Unknown scenario type: {scenario_type}")


def _validated_allocations(config: Dict[str, Any], catalog: Dict[str, Any], count: int) -> List[Dict[str, Any]]:
    allocations = config.get("allocations")
    categories = catalog.get("categories")
    if not isinstance(allocations, list) or not isinstance(categories, dict):
        raise ScenarioConfigurationError("Scenario allocations and catalog categories are required")
    if count != config.get("totalCount"):
        raise ScenarioConfigurationError(f"Canonical configuration requires count={config.get('totalCount')}")
    if sum(item.get("count", 0) for item in allocations if isinstance(item, dict)) != count:
        raise ScenarioConfigurationError("Allocation counts do not match requested total")
    allocated_types = {item.get("scenarioType") for item in allocations if isinstance(item, dict)}
    if allocated_types != set(SCENARIO_TYPES) or set(categories) != set(SCENARIO_TYPES):
        raise ScenarioConfigurationError("Catalog and allocations must contain exactly the 14 TRAIN-04 categories")
    for item in allocations:
        if item.get("difficulty") not in DIFFICULTIES or not isinstance(item.get("count"), int) or item["count"] <= 0:
            raise ScenarioConfigurationError("Each allocation needs a supported difficulty and positive integer count")
        variants = categories[item["scenarioType"]].get("variants")
        if not isinstance(variants, list) or not variants or len(variants) != len(set(variants)):
            raise ScenarioConfigurationError(f"Category variants are missing or duplicated: {item['scenarioType']}")
    return allocations


def generate_scenarios(config: Dict[str, Any], catalog: Dict[str, Any], *, seed: int, count: int) -> Tuple[List[Dict[str, Any]], Dict[str, Any]]:
    allocations = _validated_allocations(config, catalog, count)
    records = []
    index = 1
    for allocation in allocations:
        scenario_type = allocation["scenarioType"]
        difficulty = allocation["difficulty"]
        variants = catalog["categories"][scenario_type]["variants"]
        for ordinal in range(1, allocation["count"] + 1):
            per_record_seed = _record_seed(seed, scenario_type, ordinal)
            rng = random.Random(per_record_seed)
            variant = variants[(ordinal - 1) % len(variants)]
            input_data = _base(index, config["analysisDate"], rng)
            _build(scenario_type, variant, input_data, rng)
            records.append({
                "scenarioId": f"SCN-{index:06d}",
                "scenarioType": scenario_type,
                "difficulty": difficulty,
                "generatorVersion": config["generatorVersion"],
                "seed": per_record_seed,
                "variantId": variant,
                "input": input_data,
            })
            index += 1
    content = dataset_bytes(records)
    by_category = Counter(record["scenarioType"] for record in records)
    by_difficulty = Counter(record["difficulty"] for record in records)
    by_variant = Counter(record["variantId"] for record in records)
    warnings = Counter(warning for record in records for warning in record["input"]["deterministicWarnings"])
    fields = sorted(records[0]["input"])
    nulls = {field: sum(record["input"].get(field) is None for record in records) for field in fields}
    report = {
        "formatVersion": "1.0",
        "generatorVersion": config["generatorVersion"],
        "seed": seed,
        "totalCount": len(records),
        "datasetSha256": sha256_bytes(content),
        "configSha256": sha256_bytes((canonical_json(config) + "\n").encode("utf-8")),
        "catalogSha256": sha256_bytes((canonical_json(catalog) + "\n").encode("utf-8")),
        "byCategory": dict(sorted(by_category.items())),
        "byCategoryPercent": {key: round(value / len(records), 6) for key, value in sorted(by_category.items())},
        "byDifficulty": dict(sorted(by_difficulty.items())),
        "byDifficultyPercent": {key: round(value / len(records), 6) for key, value in sorted(by_difficulty.items())},
        "byVariant": dict(sorted(by_variant.items())),
        "warningCounts": dict(sorted(warnings.items())),
        "nullCountsByInputField": nulls,
    }
    return records, report
