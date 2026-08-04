# Gemma 3 4B IT — Base Baseline v1

Status: canonical RunPod execution pending.

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

Raw outputs and resumable run state remain under ignored `outputs/` or `raw/` paths until they have been sanitized and intentionally promoted. This README does not mark TRAIN-03 complete.
