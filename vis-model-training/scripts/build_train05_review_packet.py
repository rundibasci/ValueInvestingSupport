#!/usr/bin/env python3
"""Build a readable TRAIN-05 human-review packet without raw model output."""

import argparse
import json
from pathlib import Path


def _jsonl(path: Path, key: str):
    records = {}
    with path.open(encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, 1):
            if not line.strip():
                continue
            record = json.loads(line)
            record_key = record.get(key)
            if not isinstance(record_key, str) or record_key in records:
                raise ValueError(f"Invalid or duplicate {key} at {path}:{line_number}")
            records[record_key] = record
    return records


def _json_block(value):
    return "```json\n" + json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n```"


def _table_text(value):
    return str(value).replace("|", "\\|").replace("\n", " ")


def build_packet(scenarios_path: Path, candidates_path: Path, critics_path: Path, review_path: Path) -> str:
    scenarios = _jsonl(scenarios_path, "scenarioId")
    candidates = _jsonl(candidates_path, "candidateId")
    critics = _jsonl(critics_path, "candidateId")
    form = json.loads(review_path.read_text(encoding="utf-8"))
    reviews = form.get("reviews")
    if not isinstance(reviews, list) or len(reviews) < int(form.get("minimumReviews", 30)):
        raise ValueError("Review form does not satisfy its minimum review count")

    selected = []
    for review in reviews:
        candidate_id = review.get("candidateId")
        candidate = candidates.get(candidate_id)
        critic = critics.get(candidate_id)
        if candidate is None or critic is None:
            raise ValueError(f"Missing candidate or critic for {candidate_id}")
        scenario = scenarios.get(candidate.get("scenarioId"))
        if scenario is None:
            raise ValueError(f"Missing scenario for {candidate_id}")
        if critic.get("parsedReview") is None:
            raise ValueError(f"Critic review is not usable for {candidate_id}")
        selected.append((review, scenario, candidate, critic))

    lines = [
        "# TRAIN-05 — Calibration human-review packet",
        "",
        "> Materiale sintetico offline. Nessun candidato è approvato o promosso automaticamente nel training.",
        "",
        "## Istruzioni",
        "",
        "Per ogni caso indicare reviewer, data UTC, decisione (`ACCEPT` o `REJECT`), quattro punteggi da 0 a 2 e note sintetiche.",
        "",
        "- `0`: errato, non supportato o insicuro.",
        "- `1`: parzialmente corretto; richiede modifiche sostanziali.",
        "- `2`: corretto, supportato e adatto come materiale candidato.",
        "- La critic review è supporto alla decisione, non sostituisce la review umana.",
        "- `RECOVERED_REVIEW` indica JSON valido recuperato da una singola fence Markdown; non è canonical.",
        "",
        "## Indice",
        "",
        "| # | Candidate | Categoria | Stato candidate | Stato critic | Decisione |",
        "|---:|---|---|---|---|---|",
    ]
    for number, (_, _, candidate, critic) in enumerate(selected, 1):
        lines.append(
            f"| {number} | `{_table_text(candidate['candidateId'])}` | "
            f"{_table_text(candidate['scenarioType'])} | {_table_text(candidate['status'])} | "
            f"{_table_text(critic['status'])} | _da compilare_ |"
        )

    for number, (review, scenario, candidate, critic) in enumerate(selected, 1):
        lines.extend([
            "",
            f"## {number}. {candidate['candidateId']}",
            "",
            f"- Scenario: `{scenario['scenarioId']}` — `{scenario['scenarioType']}` — difficoltà `{scenario['difficulty']}`",
            f"- Candidate status: `{candidate['status']}`",
            f"- Critic status: `{critic['status']}`",
            "",
            "### Scenario input",
            "",
            _json_block(scenario["input"]),
            "",
            "### Candidate output strutturato",
            "",
            _json_block(candidate["parsedOutput"]),
            "",
            "### Validator deterministici",
            "",
            _json_block({
                "structuralErrors": candidate.get("structuralErrors", []),
                "semanticErrors": candidate.get("semanticErrors", []),
            }),
            "",
            "### Critic review",
            "",
            _json_block(critic["parsedReview"]),
            "",
            "### Review umana — da compilare",
            "",
            f"- Reviewer alias: `{review.get('reviewerAlias') or ''}`",
            f"- Reviewed at (UTC): `{review.get('reviewedAt') or ''}`",
            "- Decisione (`ACCEPT`/`REJECT`): `________________`",
            "",
            "| Dimensione | Punteggio 0–2 |",
            "|---|---:|",
            "| Grounding |  |",
            "| Classification |  |",
            "| Risk coverage |  |",
            "| Decision-support safety |  |",
            "",
            "Note:  ",
            "_Compilare qui._",
        ])

    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--scenarios", type=Path, required=True)
    parser.add_argument("--candidates", type=Path, required=True)
    parser.add_argument("--critics", type=Path, required=True)
    parser.add_argument("--review-form", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        raise SystemExit(f"Refusing to overwrite existing packet: {args.output}")
    packet = build_packet(args.scenarios, args.candidates, args.critics, args.review_form)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(packet, encoding="utf-8")
    print(json.dumps({"output": str(args.output), "reviewCount": packet.count("### Review umana — da compilare")}, sort_keys=True))


if __name__ == "__main__":
    main()
