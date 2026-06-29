# VM1 - Valuation Engine Backend Enhancements Plan

1. Spec and model foundation
   - Create this phase specification.
   - Inspect existing valuation entities, repositories, migrations, and tests.
   - Add VM1 configuration properties for WACC, owner earnings, and composite behavior.

2. Calculator and service layer
   - Extend DCF results with terminal value percentage and high-terminal-dependence.
   - Add DCF sensitivity matrix service.
   - Add WACC calculator/service using available snapshot and quote inputs with fallback metadata.
   - Add EPV calculator with RULE-08 history guard.
   - Add owner earnings calculator with configurable maintenance-capex ratio.
   - Add Graham criteria checklist service with pass/fail/insufficient-data criteria.
   - Add composite-weight validation and high-terminal-dependence rebalance logic.

3. Persistence
   - Add Flyway migration for VM1 columns/tables.
   - Extend JPA entities and repositories for WACC, Graham checklist, and composite preferences.
   - Persist VM1 results from `ValuationService.calculate`.

4. Tests
   - Add unit tests for WACC, DCF sensitivity dimensions, EPV guard/calculation, owner earnings formula, Graham criteria evaluation, and composite rebalancing.
   - Update existing DCF tests for terminal value fields.

5. Validation and merge
   - Run backend Maven tests.
   - Review git diff/status.
   - Commit, push branch, update changelog, merge to `main`, and push `main` if validation passes.
