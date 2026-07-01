# PW2 Professional Workflow Frontend Plan

1. API client coverage
   - Add typed frontend client helpers for research decision audit history, investment checklists, checklist evaluation, professional insights, advisor acknowledgement, and competence preferences.
   - Keep request and response types aligned with PW1 backend DTOs and tolerate optional fields where existing data may be absent.

2. Audit and checklist screens
   - Add a dedicated `/audit` route that shows chronological decision history with platform-state fields and CSV export.
   - Add a dedicated checklist management screen for creating, editing, deleting, and reviewing criteria.
   - Add checklist evaluation affordances on the security review page using the active checklist.

3. Review, screener, and portfolio integration
   - Show valuation confidence and factor breakdown on the security review page.
   - Show verification warnings inline on the security review page.
   - Add competence indicators on review and screener surfaces, plus a competence-only screener toggle when preferences exist.
   - Show the ADVISOR compliance banner on portfolio workflows once per session with acknowledgement.

4. Navigation and settings
   - Add navigation entries for decision history and checklist workflow where consistent with the existing shell.
   - Add competence sector editing to account/settings surfaces.

5. Validation
   - Run `npm run typecheck` and `npm run build` in `frontend`.
   - Confirm generated spec files are non-empty and review `git status` before merge.
