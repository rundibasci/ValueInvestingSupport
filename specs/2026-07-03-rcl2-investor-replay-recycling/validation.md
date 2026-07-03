# Validation - Phase RCL2: Replay-To-Backlog Feedback Loop

## Acceptance Checks

- Replay protocol describes investor-agent and monitor-agent responsibilities.
- Triage template includes severity, category, owner, affected route/API, reproduction path, evidence, status, and target phase.
- Dry-run replay cycle generates non-empty manifest, investor report, monitor report, triage backlog, and decision-support boundary notes.
- Generated reports avoid investable/personalized-advice language.
- Gate rules require two consecutive clean cycles before K1 and block unresolved high/medium findings.

## Validation Commands

Run from repository root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\rcl2-investor-replay-recycling.ps1 -SkipLiveApi -CycleId dry-run-rcl2
Get-ChildItem specs\2026-07-03-rcl2-investor-replay-recycling\evidence\dry-run-rcl2 -File
```

## Validation Results

- `powershell -ExecutionPolicy Bypass -File scripts\rcl2-investor-replay-recycling.ps1 -SkipLiveApi -CycleId dry-run-rcl2` - passed; wrote the dry-run cycle under `specs/2026-07-03-rcl2-investor-replay-recycling/evidence/dry-run-rcl2`.
- `Get-ChildItem specs\2026-07-03-rcl2-investor-replay-recycling\evidence\dry-run-rcl2 -File` - passed; generated `manifest.json`, `investor-report.md`, `monitor-report.md`, `triage-backlog.md`, and `decision-support-boundary.md`, all non-empty.

## Merge Readiness

- Spec files are present and non-empty.
- Script dry-run passes and writes replay artifacts.
- Changelog and Obsidian activity note are updated.
- Worktree staging excludes unrelated beta screenshots, generated logs, and replay artifacts unless they are part of this RCL2 evidence package.
