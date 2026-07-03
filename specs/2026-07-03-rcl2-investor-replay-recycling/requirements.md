# Requirements - Phase RCL2: Replay-To-Backlog Feedback Loop

## Scope

Phase RCL2 turns the investor-agent and monitor-agent recycling work into a repeatable validation protocol before Group K. The phase must produce structured evidence that can be rerun after each RCL fix batch and converted into roadmap backlog items when new problems appear.

## Required Outcomes

- Define a two-agent replay protocol:
  - Investor agent explores watchlist, screener, portfolio, and security review workflows from a conservative investor perspective.
  - Monitor agent correlates backend/frontend/Docker logs, HTTP status evidence, console observations, and route context for each reported issue.
- Store replay output under this phase directory with:
  - cycle manifest;
  - investor report;
  - monitor log correlation report;
  - triage backlog table;
  - decision-support boundary notes.
- Provide a triage template that classifies each finding as one of:
  - data-quality gap;
  - UI contradiction;
  - API validation defect;
  - provider limitation;
  - accessibility issue;
  - product follow-up.
- Make recycling gate rules explicit:
  - high/medium findings fail the gate;
  - findings must be fixed in RCL or explicitly deferred with owner, rationale, and target phase;
  - two consecutive clean cycles are required before K1 readiness.
- Include at least one dry-run replay artifact proving the protocol and artifact structure.

## Decisions

- RCL2 does not claim that any generated shortlist or portfolio is investable. All reports use decision-support language only.
- Dry-run evidence may validate artifact generation without a running local stack. Live replay evidence must be collected when the real-demo stack is available.
- The protocol is intentionally file-based so beta testers, agents, and humans can inspect or edit findings before they become roadmap work.

## Out Of Scope

- Fixing KO chart/history/FCF issues; those belong to RCL3.
- Running the full beta tester matrix and real `Portfolio.csv` scenario; those belong to RCL4.
- Starting Group K deployment work.
