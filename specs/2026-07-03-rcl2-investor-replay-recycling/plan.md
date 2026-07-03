# Plan - Phase RCL2: Replay-To-Backlog Feedback Loop

1. Define replay protocol and artifact model.
   - Capture investor-agent routes, decisions, screenshots, payloads, and shortlist rationale.
   - Capture monitor-agent log excerpts, statuses, correlation identifiers, and severity tags.

2. Create reusable triage template.
   - Include severity, category, owner, route/API, reproduction path, evidence links, and target phase.
   - Encode recycling gate rules for high/medium findings.

3. Add replay evidence generator.
   - Provide a PowerShell runner that creates cycle directories and baseline reports.
   - Support dry-run artifact generation and live endpoint probes.

4. Produce first cycle evidence.
   - Run the generator in dry-run mode.
   - Record decisions and validation output in the phase validation file and Obsidian activity note.

5. Merge readiness.
   - Validate generated artifacts are present and non-empty.
   - Update changelog, commit, push, merge, and continue to RCL3 unless Group K is next.
