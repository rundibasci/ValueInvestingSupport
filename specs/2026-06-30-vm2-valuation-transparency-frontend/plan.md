# VM2 - Valuation Transparency Frontend Plan

1. Spec and API contract
   - Create this phase specification.
   - Inspect the review page and VM1 backend persistence.
   - Add read-only DTO fields for valuation transparency data required by VM2.

2. Backend review exposure
   - Add repository lookup methods for WACC and Graham checklist rows by valuation result.
   - Extend `ValuationDetailResponse` with terminal dependence, EPV, owner earnings, WACC inputs, Graham checklist, and derived DCF sensitivity data.
   - Update review service tests to cover the new transparency payload.

3. Frontend transparency UI
   - Extend the review page TypeScript types.
   - Add a DCF sensitivity table with margin-of-safety coloring and base-case emphasis.
   - Add a terminal-value warning when dependence exceeds the 70% guard.
   - Add WACC, EPV, owner earnings, Graham checklist, and local composite-weight controls.

4. Validation
   - Run backend tests affected by the review DTO/service.
   - Run frontend typecheck/build.
   - Review git diff/status and keep runtime log files untouched.

5. Merge
   - Commit the VM2 spec, implementation, tests, and changelog update.
   - Push the phase branch, merge to `main`, and push `main` if validation passes.
