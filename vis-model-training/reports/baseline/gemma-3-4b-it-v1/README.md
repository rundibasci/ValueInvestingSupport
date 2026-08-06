# Gemma 3 4B IT — Base Baseline v1

Status: TRAIN-03 canonical baseline complete.

The baseline directory will contain only reviewed, sanitized, compact artifacts:

```text
environment.json        non-secret hardware and software provenance
run-manifest.json       model, revision, prompt, dataset and decoding hashes
metrics.json            global and per-category automatic metrics
manual-review.json      completed review of at least 20 cases
error-analysis.md       principal failures and at least three improvement targets
cost.json               duration and compute/storage cost without billing details
checksums.sha256        exported artifact integrity
```

Raw outputs remain under ignored `outputs/`; they are not committed. The compact artifacts in this directory were sanitized, reviewed and intentionally promoted. `cost.json` records the known hourly rate and canonical inference estimate; the final provider invoice total was not captured and is not inferred.
