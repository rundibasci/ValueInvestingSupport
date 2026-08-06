# TRAIN-04 — Scenario Distribution v1

Status: complete.

The canonical TRAIN-04 dataset contains 500 deterministic, synthetic input-only scenarios generated with seed `20260806` and generator version `1.0.0`. It contains no teacher or assistant outputs, real-company data, network-derived content, or secrets.

| Difficulty | Cases | Share |
|---|---:|---:|
| Ordinary | 300 | 60% |
| Difficult | 125 | 25% |
| Adversarial or incomplete | 75 | 15% |

All 14 scenario categories and all 28 catalog variants are represented. The dataset SHA-256 is `299b704a77ad8799f9b755a18f9462b32480f9732a654e44ff87e1bd8152ddad`.

`distribution-v1.json` records counts and percentages by category, difficulty and variant, plus warning and null-field counts and hashes of the controlling configuration. `checksums-v1.sha256` verifies the canonical config, catalog, dataset and report.

TRAIN-03 failure modes have explicit coverage for valuation bias, overvaluation direction, payout alarms, unsupported threshold judgments and adversarial review. TRAIN-05 may consume these inputs only after preserving their provenance and keeping teacher outputs separate.
