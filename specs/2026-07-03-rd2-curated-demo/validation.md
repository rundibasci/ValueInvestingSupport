# RD2-1 Validation - Agent 1 Curated Universe Walkthrough

## Acceptance Checks

- The selected phase is RD2-1 and not Group K, K1, K2, or K3.
- The replay pack documents universe curation, ingestion monitoring, screener research, deep analysis, comparison, portfolio construction, watchlist, dashboard, and alerts.
- The dry-run replay writes a manifest listing the curated universe, workflow steps, target endpoints, and live replay instructions.
- The report and screenshot checklist are stakeholder-presentable and avoid investment advice language.
- Tokens are redacted from live-mode artifacts before they are written to disk.

## Commands

```powershell
powershell -ExecutionPolicy Bypass -File scripts/rd2-agent1-curated-universe-walkthrough.ps1 -SkipLiveApi
```

```powershell
Get-ChildItem specs/2026-07-03-rd2-curated-demo -Recurse -File
```

## Manual QA

When the real-demo stack is running, execute:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/rd2-agent1-curated-universe-walkthrough.ps1
```

Then collect the screenshots listed in `screenshots/README.md` and update `walkthrough-report.md` with observed pass/fail evidence and RD1 comparison notes.

## Known Risks

- Live endpoint names may differ as the UI/API evolves; the replay script captures those failures as evidence for follow-up instead of masking them.
- Yahoo Finance coverage gaps may leave some curated symbols partially populated.
- Dry-run validation proves artifact generation only; it does not prove live stack behavior.

## Merge Readiness

- Dry-run command passes.
- Spec files and replay script are committed.
- Obsidian activity log is updated.
- Changelog is updated during merge.
