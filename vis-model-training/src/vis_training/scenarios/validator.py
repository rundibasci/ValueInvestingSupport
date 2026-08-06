"""Schema, semantic, uniqueness, and contamination gates for TRAIN-04."""

import json
from collections import Counter
from pathlib import Path
from typing import Any, Dict, Iterable, List, Optional, Set, Tuple

from jsonschema import Draft202012Validator, FormatChecker

from .generator import DIFFICULTIES, SCENARIO_TYPES, _mos
from .io import canonical_json, load_jsonl, load_object


def _existing_identity(paths: Iterable[Path]) -> Tuple[Set[str], Set[str], Set[str]]:
    ids, symbols, inputs = set(), set(), set()
    for path in paths:
        for record in load_jsonl(path):
            metadata = record.get("metadata", {})
            if isinstance(metadata, dict) and isinstance(metadata.get("exampleId"), str):
                ids.add(metadata["exampleId"])
            messages = record.get("messages", [])
            if not isinstance(messages, list):
                continue
            for message in messages:
                if isinstance(message, dict) and message.get("role") == "user" and isinstance(message.get("content"), str):
                    try:
                        value = json.loads(message["content"])
                    except json.JSONDecodeError:
                        continue
                    if isinstance(value, dict):
                        inputs.add(canonical_json(value))
                        if isinstance(value.get("symbol"), str):
                            symbols.add(value["symbol"])
                    break
    return ids, symbols, inputs


def _schema_failures(value: Any, validator: Draft202012Validator, prefix: str) -> List[str]:
    failures = []
    for error in sorted(validator.iter_errors(value), key=lambda item: tuple(str(part) for part in item.absolute_path)):
        suffix = "".join(f"[{part}]" if isinstance(part, int) else f".{part}" for part in error.absolute_path)
        failures.append(f"{prefix}{suffix}: schema {error.validator or 'validation'} failed")
    return failures


def _semantic_failures(record: Dict[str, Any], catalog: Dict[str, Any]) -> List[str]:
    scenario_id = record.get("scenarioId", "UNKNOWN")
    scenario_type = record.get("scenarioType")
    variant = record.get("variantId")
    data = record.get("input")
    failures = []
    if scenario_type not in SCENARIO_TYPES:
        return [f"{scenario_id}: unsupported scenarioType"]
    if record.get("difficulty") not in DIFFICULTIES:
        failures.append(f"{scenario_id}: unsupported difficulty")
    allowed_variants = catalog.get("categories", {}).get(scenario_type, {}).get("variants", [])
    if variant not in allowed_variants:
        failures.append(f"{scenario_id}: variant is not declared for {scenario_type}")
    if not isinstance(data, dict):
        return failures + [f"{scenario_id}: input must be an object"]
    intrinsic = data.get("intrinsicValue")
    margin = data.get("marginOfSafetyPercent")
    mismatch_allowed = scenario_type == "INCONSISTENT_DATA" and variant == "margin-of-safety-mismatch"
    if intrinsic is None:
        if margin is not None:
            failures.append(f"{scenario_id}: marginOfSafetyPercent must be null without intrinsicValue")
    elif isinstance(intrinsic, (int, float)) and isinstance(data.get("marketPrice"), (int, float)):
        expected = _mos(data["marketPrice"], intrinsic)
        if margin != expected and not mismatch_allowed:
            failures.append(f"{scenario_id}: margin of safety mismatch ({margin} != {expected})")
        if mismatch_allowed and margin == expected:
            failures.append(f"{scenario_id}: intentional margin mismatch is absent")
    payout = data.get("payoutRatioPercent")
    if isinstance(payout, (int, float)) and payout < 0:
        failures.append(f"{scenario_id}: negative payout lacks an explicit supported variant")
    warnings = data.get("deterministicWarnings", [])
    quality = data.get("dataQuality")
    if scenario_type == "DIVIDEND_RISK":
        if not isinstance(payout, (int, float)) or payout <= 100:
            failures.append(f"{scenario_id}: dividend risk requires payout above 100%")
        if data.get("freeCashFlowTrend") not in {"DECLINING", "STRONGLY_DECLINING"}:
            failures.append(f"{scenario_id}: dividend risk requires deteriorating free cash flow")
    if scenario_type == "VALUE_TRAP":
        if not isinstance(margin, (int, float)) or margin <= 0:
            failures.append(f"{scenario_id}: value trap requires a positive valuation discount")
        if "STRONGLY_DECLINING" not in {data.get("earningsTrend"), data.get("freeCashFlowTrend")}:
            failures.append(f"{scenario_id}: value trap requires strongly declining operating evidence")
    if scenario_type == "OVERVALUED_STRONG" and (not isinstance(margin, (int, float)) or margin >= 0):
        failures.append(f"{scenario_id}: overvaluation requires negative margin of safety")
    if scenario_type == "FAIR_VALUE" and (not isinstance(margin, (int, float)) or abs(margin) > 1.02):
        failures.append(f"{scenario_id}: fair value exceeds declared tolerance")
    if scenario_type == "STALE_DATA" and (quality != "STALE" or "STALE_FINANCIALS" not in warnings):
        failures.append(f"{scenario_id}: stale scenario requires matching quality and warning")
    if scenario_type == "INSUFFICIENT_DATA":
        if quality != "INSUFFICIENT" or intrinsic is not None or margin is not None:
            failures.append(f"{scenario_id}: insufficient scenario retains required valuation evidence")
    if scenario_type in {"CONTRADICTORY_SIGNALS", "INCONSISTENT_DATA"} and quality != "INCONSISTENT":
        failures.append(f"{scenario_id}: inconsistent evidence requires INCONSISTENT quality")
    if scenario_type == "CONTRADICTORY_SIGNALS" and "CONTRADICTORY_SIGNALS" not in warnings:
        failures.append(f"{scenario_id}: contradictory scenario requires warning")
    if scenario_type == "ADVERSARIAL_INPUT":
        if quality != "PARTIAL" or not any(isinstance(item, str) and item.startswith("UNTRUSTED_TEXT_") for item in warnings):
            failures.append(f"{scenario_id}: adversarial text must remain a marked untrusted warning")
    return failures


def validate_records(
    records: List[Dict[str, Any]],
    *,
    input_schema: Dict[str, Any],
    catalog: Dict[str, Any],
    expected_count: Optional[int] = None,
    contamination_paths: Iterable[Path] = (),
) -> Tuple[List[str], List[str]]:
    failures, contamination = [], []
    validator = Draft202012Validator(input_schema, format_checker=FormatChecker())
    ids, symbols, serialized_inputs = set(), set(), set()
    prior_ids, prior_symbols, prior_inputs = _existing_identity(contamination_paths)
    required_record_fields = {"scenarioId", "scenarioType", "difficulty", "generatorVersion", "seed", "variantId", "input"}
    if expected_count is not None and len(records) != expected_count:
        failures.append(f"dataset: expected {expected_count} records, found {len(records)}")
    for index, record in enumerate(records, start=1):
        prefix = f"record[{index}]"
        if set(record) != required_record_fields:
            failures.append(f"{prefix}: record fields do not match version 1.0 contract")
        scenario_id = record.get("scenarioId")
        data = record.get("input")
        if not isinstance(scenario_id, str) or scenario_id != f"SCN-{index:06d}":
            failures.append(f"{prefix}: scenarioId is not canonical")
        elif scenario_id in ids:
            failures.append(f"{scenario_id}: duplicate scenarioId")
        ids.add(scenario_id)
        if isinstance(data, dict):
            failures.extend(_schema_failures(data, validator, f"{scenario_id}.input"))
            symbol = data.get("symbol")
            serialized = canonical_json(data)
            if isinstance(symbol, str) and symbol in symbols:
                failures.append(f"{scenario_id}: duplicate synthetic symbol")
            if serialized in serialized_inputs:
                failures.append(f"{scenario_id}: duplicate input record")
            if isinstance(symbol, str):
                symbols.add(symbol)
            serialized_inputs.add(serialized)
            if not isinstance(symbol, str) or not symbol.startswith("SYN"):
                failures.append(f"{scenario_id}: symbol is not clearly synthetic")
            if not str(data.get("companyName", "")).startswith("Synthetic Scenario Company "):
                failures.append(f"{scenario_id}: companyName is not clearly synthetic")
            if scenario_id in prior_ids or (isinstance(symbol, str) and symbol in prior_symbols) or serialized in prior_inputs:
                contamination.append(f"{scenario_id}: identity collides with prior TRAIN dataset")
        failures.extend(_semantic_failures(record, catalog))
    category_counts = Counter(record.get("scenarioType") for record in records)
    if set(category_counts) != set(SCENARIO_TYPES):
        failures.append("dataset: all 14 scenario categories must be present")
    return failures, contamination


def validate_dataset_paths(dataset: Path, input_schema_path: Path, catalog_path: Path, contamination_paths: Iterable[Path], expected_count: Optional[int] = None):
    return validate_records(
        load_jsonl(dataset),
        input_schema=load_object(input_schema_path),
        catalog=load_object(catalog_path),
        expected_count=expected_count,
        contamination_paths=contamination_paths,
    )
