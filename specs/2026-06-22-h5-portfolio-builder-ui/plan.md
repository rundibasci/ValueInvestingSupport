# H5 — Portfolio Builder UI Plan

1. **Portfolio API and route foundation**
   - Review the existing Group F portfolio, simulation, and rebalance contracts.
   - Add typed API client functions, query keys, protected routes, navigation, and loading/error/empty states.

2. **Portfolio builder and constraint controls**
   - Build the budget, risk profile, yield target, and full allocation-constraint form.
   - Submit simulations through the existing API and display exclusions and validation feedback.
   - Render the editable proposal table and recalculate client-side constraint feedback as weights change.

3. **Allocation visualization and save flow**
   - Add the sector-allocation donut chart and accessible tabular equivalents.
   - Prevent saving while constraints are invalid; save valid proposals through the existing portfolio API.
   - Show transparent assumptions, allocation details, and the MiFID II disclaimer.

4. **Portfolio management and rebalancing**
   - Implement portfolio list/detail views and holdings CRUD interactions.
   - Add the rebalancing workflow: target inputs, proposal display, current-versus-target comparison, and trade guidance.
   - Invalidate/refetch relevant queries after each mutation.

5. **Quality and merge readiness**
   - Add focused component/form tests for constraint validation and key states.
   - Add an end-to-end browser test covering login, simulation with constraints, save, holdings view, and rebalance proposal.
   - Run lint/type checks, frontend tests, production build, and the browser test; resolve accessibility and responsive-layout issues found during verification.
