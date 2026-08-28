# Vertex AI Gemini 2.5 Flash — TA3 Candidate Report v1

Status: live capability-benchmark run complete, human review complete. Gate evaluation partial (two field groups unresolved — see `error-analysis.md`). `specs/roadmap.md` → Phase TA3 not yet marked complete; go/no-go decision for TA4 pending.

```text
environment.json        non-secret model, SDK, and decoding-config provenance
run-manifest.json       backend manifest, decoding settings, dataset list
metrics.json            global automatic metrics per dataset (base-benchmark-v1, scenarios-v1, real-ticker-knowledge-leakage-v1)
manual-review.json      68 completed human reviews across all three datasets
error-analysis.md       full comparison against the closed Gemma baseline, key findings, gate status
cost.json               token-based spend estimate for the live run
```

Full raw outputs (all 574 generations, per-dataset metrics with per-case failure lists, and the per-dataset review forms) live under `results/vertex-gemini-2.5-flash-v1/` — committed, not gitignored, since this is evidence for a production go/no-go decision rather than disposable scratch output.
