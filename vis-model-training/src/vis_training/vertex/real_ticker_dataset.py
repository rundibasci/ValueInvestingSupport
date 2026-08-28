"""Build the TA3 real-ticker knowledge-leakage benchmark set.

TRAIN-04's 500 scenarios all use synthetic `VIS*`-prefixed symbols
specifically to prevent the evaluated model from drawing on pretrained
knowledge (specs/roadmap.md → Phase TA3). That design cannot test whether a
large general-purpose model like Gemini overrides *supplied* evidence with
what it actually knows about a *real, well-known* company — a materially
bigger risk for Gemini than it was for the smaller, domain-scoped Gemma
checkpoint TRAIN-05 evaluated.

Each case here uses a real, famous company and a `thesis-input.schema.json`
value deliberately altered to contradict a well-established real-world fact
about that company. `expected` is computed strictly from the *supplied*
(deliberately wrong) evidence, following the system prompt's rules exactly
— never from what is actually true about the real company. A thesis that
matches `expected` followed the supplied evidence correctly; a thesis that
instead reflects the real-world fact is the knowledge-leakage failure mode
this set exists to surface (scored by a human reviewer against the new
`knowledgeLeakage` rubric dimension — this is not mechanically detectable
by the schema/semantic validator alone, per the roadmap).

`thesis-input.schema.json` has no `sector` field, so every alteration here
uses an actual schema field (a trend, a dividend metric, or a price)
contradicting a real fact about that company's actual trend/dividend/price
history — not an invented field.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Dict, List

from .expected_thesis import derive_expected_thesis

DATASET_FORMAT_VERSION = "1.0"
CATEGORY = "REAL_TICKER_KNOWLEDGE_LEAKAGE"

# vis-model-training/ root, resolved relative to this file rather than cwd,
# so this module works regardless of the caller's working directory.
_REPO_ROOT = Path(__file__).resolve().parents[3]
SYSTEM_PROMPT_PATH = _REPO_ROOT / "prompts" / "system-prompt-v3.txt"

# Each company entry records the REAL, well-established fact this dataset
# relies on (so a reviewer can check the premise), never used in `input` —
# only the deliberately altered value is used in `input`.
_COMPANIES = [
    {
        "symbol": "AAPL",
        "companyName": "Apple Inc.",
        "realFact": (
            "Apple has generated strongly positive, growing free cash flow "
            "for well over a decade; it is one of the most cash-generative "
            "companies in the world."
        ),
    },
    {
        "symbol": "MSFT",
        "companyName": "Microsoft Corporation",
        "realFact": (
            "Microsoft's revenue and earnings have been strongly growing for "
            "years, driven substantially by its cloud (Azure) business."
        ),
    },
    {
        "symbol": "KO",
        "companyName": "The Coca-Cola Company",
        "realFact": (
            "Coca-Cola is a Dividend King with over six decades of "
            "consecutive annual dividend increases — one of the most famous "
            "examples of sustained dividend growth in public markets."
        ),
    },
    {
        "symbol": "JNJ",
        "companyName": "Johnson & Johnson",
        "realFact": (
            "Johnson & Johnson is a Dividend King with over six decades of "
            "consecutive annual dividend increases, widely cited as a "
            "hallmark of financial stability."
        ),
    },
    {
        "symbol": "PG",
        "companyName": "The Procter & Gamble Company",
        "realFact": (
            "Procter & Gamble is a Dividend King with over six decades of "
            "consecutive annual dividend increases."
        ),
    },
    {
        "symbol": "XOM",
        "companyName": "Exxon Mobil Corporation",
        "realFact": (
            "ExxonMobil's earnings are widely known to be cyclical, closely "
            "tracking global oil and gas prices, rather than being a "
            "smooth, steadily growing trend."
        ),
    },
]

# Each variant defines: which input field(s) to override, what the
# deliberately-wrong value is, and how that contradicts the company's real
# fact above. `expected` is derived generically in build_dataset() from
# these overrides plus the shared baseline input, following only the
# supplied (fake) evidence — never the real fact.
_VARIANTS = [
    {
        "variantId": "fabricated-fcf-collapse",
        "description": "Fabricates a STRONGLY_DECLINING free-cash-flow trend for a company famous for strong, stable cash generation.",
        "overrides": {"freeCashFlowTrend": "STRONGLY_DECLINING"},
    },
    {
        "variantId": "fabricated-earnings-collapse",
        "description": "Fabricates a STRONGLY_DECLINING earnings trend for a company known for steady or growing earnings.",
        "overrides": {"earningsTrend": "STRONGLY_DECLINING"},
    },
    {
        "variantId": "fabricated-dividend-cut-signal",
        "description": "Fabricates an unsustainably high payout ratio (contradicting a long, stable dividend-growth record) to test whether the model states real dividend history instead of only the supplied ratio.",
        "overrides": {"payoutRatioPercent": 145.0, "dividendYieldPercent": 6.8},
    },
    {
        "variantId": "fabricated-overvaluation",
        "description": "Fabricates a materially negative margin of safety (price far above the supplied intrinsic value) for a well-known company, to test whether the model substitutes a real-world price/valuation opinion for the supplied numbers.",
        "overrides": {"marginOfSafetyPercent": -55.0, "valueScore": 22.0},
    },
]

_BASE_INPUT_DEFAULTS: Dict[str, Any] = {
    "analysisDate": "2026-08-27",
    "marketPrice": 150.0,
    "intrinsicValue": 120.0,
    "marginOfSafetyPercent": -25.0,
    "valueScore": 55.0,
    "dividendYieldPercent": 1.5,
    "payoutRatioPercent": 35.0,
    "netDebtToEbitda": 1.2,
    "revenueTrend": "GROWING",
    "earningsTrend": "GROWING",
    "freeCashFlowTrend": "GROWING",
    "dataQuality": "COMPLETE",
    "deterministicWarnings": [],
}


def _build_input(company: Dict[str, Any], variant: Dict[str, Any]) -> Dict[str, Any]:
    input_data = dict(_BASE_INPUT_DEFAULTS)
    input_data["symbol"] = company["symbol"]
    input_data["companyName"] = company["companyName"]
    input_data.update(variant["overrides"])
    return input_data


def build_dataset() -> List[Dict[str, Any]]:
    system_prompt = Path(SYSTEM_PROMPT_PATH).read_text(encoding="utf-8")
    records = []
    counter = 0
    for company in _COMPANIES:
        for variant in _VARIANTS:
            counter += 1
            input_data = _build_input(company, variant)
            expected = derive_expected_thesis(input_data)
            example_id = f"RT-{counter:03d}"
            records.append(
                {
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": json.dumps(input_data, sort_keys=True)},
                        {"role": "assistant", "content": json.dumps(expected, sort_keys=True)},
                    ],
                    "metadata": {
                        "benchmarkCategory": CATEGORY,
                        "datasetVersion": DATASET_FORMAT_VERSION,
                        "exampleId": example_id,
                        "scenarioType": f"{CATEGORY}_{variant['variantId'].upper()}",
                        "source": "REAL_TICKER_MANUAL_TEMPLATE",
                        "realTicker": {
                            "symbol": company["symbol"],
                            "companyName": company["companyName"],
                            "realFact": company["realFact"],
                            "variantId": variant["variantId"],
                            "alterationDescription": variant["description"],
                            "alteredFields": sorted(variant["overrides"].keys()),
                        },
                    },
                }
            )
    return records


def write_dataset(output_path: Path) -> int:
    records = build_dataset()
    with open(output_path, "w", encoding="utf-8") as handle:
        for record in records:
            handle.write(json.dumps(record, sort_keys=True) + "\n")
    return len(records)
