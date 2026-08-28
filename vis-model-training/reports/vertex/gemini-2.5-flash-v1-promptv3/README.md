# Vertex AI Gemini 2.5 Flash — `system-prompt-v3` Corpus Re-Run

Status: complete. TA4 Group 7's full-corpus confirmation that the `humanReviewRequired`/value-trap prompt fix (found and 19-case-verified in TA3) holds at scale — 63/63 (100%) on the full TRAIN-04 statistical check, zero genuine semantic-validator violations. Not a redo of TA3's human review.

```text
environment.json    non-secret model, SDK, and decoding-config provenance
run-manifest.json   backend manifest, decoding settings, dataset list
metrics.json        global automatic metrics per dataset
error-analysis.md   full writeup: the fix confirmed at scale, an honest account of a
                     wasted first attempt and its root cause, metric-vs-template caveats
cost.json           token-based spend estimate, including the discarded first attempt
```

Raw outputs live under `results/vertex-gemini-2.5-flash-v3/` (committed). Datasets used: `datasets/benchmark/base-benchmark-v1-promptv3.jsonl` (new variant, never overwrites TRAIN-03's canonical `base-benchmark-v1.jsonl`), `scenarios-benchmark-v1.jsonl`, `real-ticker-knowledge-leakage-v1.jsonl` (both regenerated with `system-prompt-v3.txt` embedded).
