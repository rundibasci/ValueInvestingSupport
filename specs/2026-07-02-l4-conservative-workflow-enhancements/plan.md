# L4 Conservative Workflow Enhancements - Plan

1. Backend conservative screener support
   - Add a conservative research preset model that captures positive margin of safety, score availability, dividend coverage, leverage/liquidity resilience, and data completeness.
   - Expose deterministic preset metadata and empty-state diagnostic guidance through authenticated APIs.
   - Add selected-symbol comparison data for the Agent 1 workflow without requiring live provider calls.

2. Backend tests
   - Verify the conservative preset contains all required criteria and preserves decision-support wording.
   - Verify empty-state diagnostics identify likely eliminating criteria and suggested relaxations without mutating criteria.
   - Verify comparison rows include MoS, value score, quality, leverage/liquidity, growth, dividend, and source/data coverage fields.

3. Frontend conservative workflow UI
   - Add a conservative preset affordance and empty-state diagnostic panel to the screener page.
   - Add selected-symbol comparison for the Agent 1 workflow using existing seeded/local symbols where possible.
   - Keep watchlist rationale support focused on "wait for better price" and data-quality-gap workflows; defer broader research notes unless implementation proves the existing field insufficient.

4. Frontend validation
   - Run typecheck and production build.
   - Inspect the affected pages for copy that could imply personalized advice.

5. Evidence, documentation, and merge
   - Record deterministic replay evidence for the 10-stock Agent 1 workflow under this spec directory.
   - Update validation evidence and the Obsidian activity log.
   - Commit, push, update changelog, merge into `main`, and push `main` when validation passes.
