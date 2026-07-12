You are Codex running inside the ValueInvestingSupport repository.

Goal: fix exactly one persisted chart defect, then verify it.

Bug to fix:
~~~json
{
    "id":  "CHART-INGR-RATIOS-REPEATED-SERIES",
    "status":  "in_progress",
    "severity":  "high",
    "route":  "/securities/INGR",
    "surface":  "Ratios tab",
    "title":  "INGR ratio charts show repeated synthetic history",
    "observed":  "Ratios rows=10, distinct PE/ROIC/ROE values=1/0/1.",
    "expected":  "Historical ratio charts should plot distinct observed periods, or show a data gap instead of repeated copied values.",
    "reproduction":  "Open http://localhost:5173/securities/INGR, select Ratios, switch chart periods.",
    "suggestedOwner":  "frontend/backend",
    "detectedAt":  "2026-07-10T18:24:42.2392457+02:00",
    "lastSeenAt":  "2026-07-10T18:44:38.3736698+02:00",
    "evidence":  {
                     "ok":  true,
                     "rowCount":  10,
                     "peDistinct":  1,
                     "roicDistinct":  0,
                     "roeDistinct":  1,
                     "dates":  [
                                   "2026-07-03",
                                   "2025-12-31",
                                   "2024-12-31",
                                   "2023-12-31",
                                   "2022-12-31",
                                   "2021-12-31",
                                   "2020-12-31",
                                   "2019-12-31",
                                   "2018-12-31",
                                   "2017-12-31"
                               ]
                 },
    "lastFixAttemptAt":  "2026-07-12T21:25:28.0531809+02:00",
    "fixAttemptCount":  2,
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
