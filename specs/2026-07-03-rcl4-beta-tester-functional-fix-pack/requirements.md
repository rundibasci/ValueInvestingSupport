# Requirements - Phase RCL4: Beta Tester Functional Fix Pack

## Scope

Phase RCL4 consolidates beta tester findings before Group K. It defines repeatable beta cycles for investor, advisor/compliance, UI/accessibility, data-quality/API, and real-portfolio CSV personas, and requires reruns until two consecutive cycles report no new high- or medium-severity defects.

## Required Outcomes

- Provide a beta tester protocol that covers:
  - investor research workflow;
  - advisor/compliance wording and acknowledgement workflow;
  - UI/accessibility smoke checks;
  - data-quality/API validation checks;
  - real-portfolio CSV tester using `C:\Users\Marcello\Downloads\Portfolio.csv`.
- Produce a structured report per cycle with severity, owner, route/API, reproduction path, evidence, and disposition.
- Parse the real portfolio CSV in a non-destructive way and report:
  - detected headers;
  - row count;
  - probable symbol column;
  - probable quantity/weight/value columns;
  - duplicate symbols;
  - unsupported or blank symbols;
  - Berkshire symbol normalization notes.
- Enforce the gate:
  - any new high/medium finding requires a fix, accepted deferral, or target RCL/K-blocker owner;
  - two consecutive clean beta cycles are required before K1.

## Decisions

- RCL4 produces the beta testing harness and first dry-run evidence. Live beta cycles must be rerun with the app stack and portfolio import/mapping UI available.
- The real portfolio CSV is read for validation only; this phase does not mutate portfolios or upload holdings automatically.
- Reports remain decision-support QA artifacts and must not describe holdings as personalized advice.

## Out Of Scope

- Starting K1 deployment.
- Automatically trading, recommending, or optimizing the CSV portfolio.
- Destructive cleanup of user portfolios or watchlists.
