# Chart QA Agent

This workspace persists chart defects found on security detail pages and drives a scheduled fix loop.

## Files

- `scripts/chart-qa-agent.ps1` probes the demo APIs behind the chart tabs and writes persistent bugs to `specs/chart-qa-agent/bugs.json`.
- `specs/chart-qa-agent/evidence/` stores the raw API evidence for each audit pass.
- `scripts/chart-bug-fix-cycle.ps1` picks one open bug, asks Codex to fix it, then reruns the audit.
- `scripts/register-chart-bug-fix-agent.ps1` registers a Windows scheduled task that launches one fix cycle every 3 hours for a 10-cycle window.

## Current Scope

The first target is `http://localhost:5173/securities/INGR`, covering quote history, financials, ratios, and dividends chart data. Add symbols through the `-Symbols` and `-Routes` parameters when the same checks need to expand to other security pages.

## Manual Commands

Run the audit:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/chart-qa-agent.ps1
```

Run one fix cycle immediately:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/chart-bug-fix-cycle.ps1 -RunOnce -NoDelay
```

Register the 10-cycle scheduled agent:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/register-chart-bug-fix-agent.ps1
```
