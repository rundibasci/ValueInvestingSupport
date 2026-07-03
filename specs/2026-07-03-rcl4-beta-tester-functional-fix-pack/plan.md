# Plan - Phase RCL4: Beta Tester Functional Fix Pack

1. Define beta tester matrix.
   - Investor, advisor/compliance, UI/accessibility, data-quality/API, and real-portfolio CSV personas.

2. Add beta cycle runner.
   - Generate cycle manifest, persona reports, CSV portfolio inspection, and beta gate report.
   - Support dry-run mode without a running app stack.

3. Run first cycle.
   - Execute the dry-run against the configured CSV path.
   - Record validation and any CSV availability findings.

4. Document gate.
   - Require two consecutive clean cycles before K1.
   - Carry findings into RCL/K blocker backlog with owner and rationale.

5. Merge readiness.
   - Update validation, Obsidian activity note, changelog, commit, push, and merge.
   - Stop when next roadmap group is K.
