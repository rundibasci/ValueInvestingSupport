You are Codex running inside the ValueInvestingSupport repository.

Goal: fix exactly one persisted chart defect, then verify it.

Bug to fix:
~~~json
{
    "id":  "CHART-INGR-FINANCIALS-10Y-DEPTH",
    "status":  "in_progress",
    "severity":  "medium",
    "route":  "/securities/INGR",
    "surface":  "Financials tab",
    "title":  "INGR financial charts expose 10y controls without 10 annual points",
    "observed":  "Annual financial rows=4.",
    "expected":  "10y controls should be disabled/renamed when fewer than 10 distinct annual periods exist.",
    "reproduction":  "Open http://localhost:5173/securities/INGR, select Financials, then inspect chart range controls.",
    "suggestedOwner":  "frontend/backend",
    "detectedAt":  "2026-07-10T18:24:41.9879780+02:00",
    "lastSeenAt":  "2026-07-10T18:39:48.9479184+02:00",
    "evidence":  {
                     "ok":  true,
                     "annualCount":  4,
                     "years":  [
                                   2026,
                                   2025,
                                   2024,
                                   2023
                               ]
                 },
    "lastFixAttemptAt":  "2026-07-10T18:40:47.4853116+02:00",
    "fixAttemptCount":  7,
    "lastFixNote":  "Cycle 1 started."
}
~~~

Constraints:
- Keep the change focused on this bug.
- Do not revert unrelated user changes.
- Prefer existing project patterns.
- Run the most relevant tests or checks you can.
- After editing, run:
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts/chart-qa-agent.ps1
- Leave specs/chart-qa-agent/bugs.json updated by that audit.
- Commit only if the repository convention or current user explicitly asks for it; otherwise leave changes staged/unstaged for review.
