# L1 Plan - Prudent Persona Replay Pack

1. Replay scope and evidence structure
   - Create a deterministic L1 evidence folder for Agent 1's 10-symbol prudent-value set.
   - Capture per-symbol review packet evidence for score availability, valuation availability, source/freshness status, margin of safety, recommendation, and data-quality notes.
   - Store generated output under `specs/2026-07-02-l1-prudent-persona-replay-pack/evidence/`.

2. Replay runner
   - Add a PowerShell runner that can execute in dry-run mode without a live backend.
   - In live mode, authenticate, seed the 10-symbol set, open each review packet, create a 10-position equal-weight validation portfolio, verify concentration, create an oversized KO or JNJ scenario, add PG/KO/JNJ/MSFT watchlist rationale notes, and confirm watchlist persistence by reloading the list.
   - Redact tokens from any persisted output.

3. Evidence report
   - Generate a Markdown summary listing each replay area, pass/fail status, artifact path, and notes.
   - Include an explicit decision-support boundary and avoid describing the 10-stock set as investable.
   - Document provider/data limitations as workflow evidence rather than hiding them.

4. Validation and merge readiness
   - Verify spec files are present and non-empty.
   - Run the dry-run replay command.
   - Run the smallest meaningful build/test checks for touched areas.
   - Update the Obsidian activity note and merge only after validation passes.
