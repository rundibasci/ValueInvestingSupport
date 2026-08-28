# TA5 — Validation

## Acceptance Checks

- [ ] `frontend/src/api/thesis.ts` exists, exports `thesisApi` with `generate`/`status`/`latest`/`reviewQueue`, and the `Thesis`/`ThesisEvidence`/`ThesisClassification`/`EvidenceField` types match `vis-model-training/schemas/thesis-output.schema.json`'s field set and enums exactly.
- [ ] `latest()` resolves to `null` (never throws) when the backend reports `NOT_GENERATED`.
- [ ] `generate()` throws a `RateLimitError` (carrying `limit`/`resetsAt`) specifically on a `429` response, distinguishable via `instanceof` from any other thrown error.
- [ ] `ThesisPanel.tsx` renders all 7 states from `specs/roadmap.md` → Phase TA5: not-yet-generated, generating, ready, human-review-pending, stale, failed, rate-limited.
- [ ] Generation never fires automatically on mount — the panel's initial render (for a symbol with no thesis yet) shows only the call-to-action button; no `generate` mutation call happens without a user click.
- [ ] The human-review-pending banner is `role="alert"`, visually distinct (rose/amber styling matching the page's existing alert conventions), and bull/bear case content is **not** rendered alongside it.
- [ ] The stale banner appears whenever `latest.data.stale === true`, independent of `status` (a `READY` thesis can be stale), and offers a Regenerate action.
- [ ] Every bull/bear case `claim` renders its `evidenceFields` as clickable links to the correct review-page section anchor, using the exact mapping in `plan.md` → Group 2.4.
- [ ] The provenance inspector (`<details>`) shows `modelId`, `modelVersion`, `promptVersion`, `generatedAt`, `requestId`, `latencyMs`, and the full raw JSON, in every terminal state.
- [ ] The MiFID II disclaimer (`'This is a decision-support tool, not investment advice (MiFID II).'`, matching `SecurityReviewPage.tsx`'s existing fallback string/styling) renders in `ready`/`human-review-pending`/`stale`, and does **not** render in `not-yet-generated`/`generating`/`failed`/`rate-limited`.
- [ ] `AdminThesisReviewPage.tsx` redirects a non-ADMIN session to `/` before any data fetch, matching `AdminJobsPage.tsx`'s exact guard.
- [ ] The review queue lists every `HUMAN_REVIEW_PENDING`-or-non-empty-`dataWarnings` item from the backend (TA4's own query scope — TA5 does not re-filter), links each row's symbol to `/securities/{symbol}/review#thesis`, and shows an explicit empty state when the queue is empty.
- [ ] `admin/thesis-review` route exists in `App.tsx`; `AppShell.tsx`'s `adminNavigation` includes the new link, gated the same way every other admin link already is (`session?.role === 'ADMIN'`).
- [ ] No `backend/` or `vis-model-training/` file appears in this phase's diff.

## Test Strategy

This project has no frontend test runner configured (`frontend/package.json` has no `test` script; no vitest/jest/`@testing-library/*` dependency; project-wide `find . -iname "*.test.ts*"` returns zero files as of this phase). This is not a gap introduced by TA5 — it is the same constraint SR2 (`specs/2026-06-30-sr2-scoring-risk-frontend/validation.md` → Known Risks) already documented for the last comparable frontend-only phase. TA5 does not introduce a test framework (see `requirements.md` → Decisions #3); validation is:

1. `cd frontend && npm run build` — runs `tsc -b && vite build`. Zero type errors, zero build errors. This is the actual automated gate for this phase.
2. `cd backend && ./mvnw -o test` is **not required** — TA5 makes no backend change. Run only if a build-time discovery forces an unplanned backend touch (would then require updating this spec, not silently expanding scope).
3. Manual QA (see below), run against a local backend with `THESIS_AGENT_ENABLED=false` (TA4's shipped default, unchanged by this phase).

## Manual QA Sequence

`THESIS_AGENT_ENABLED=false` means the real `generate` endpoint's async Gemini call path is not exercised end-to-end in this environment (per TA4, flipping it is a separate operational decision). Verify what is actually reachable, and record explicitly which of the following were checked against a live TA4 backend versus verified by code inspection only — do not claim a state was "tested" if it was only read, matching this project's own honesty bar (TA4's `validation.md` → Corrections Made During Implementation sets this precedent).

1. Log in, open `/securities/{seeded-symbol}/review`, confirm the new "AI Investment Thesis" section (`#thesis`) renders after "Risk And Data Quality Caveats" and before "Next Actions".
2. For a symbol with no prior thesis: confirm only the call-to-action renders; confirm no network call to `/thesis/generate` fires on page load (inspect the browser network tab).
3. Click "Generate AI Thesis": confirm a `202` triggers the generating state and polling begins at the returned `pollingIntervalMs`.
4. Reach a terminal state (via a real TA4 backend call, or a stubbed/mocked response if `THESIS_AGENT_ENABLED=false` blocks a real terminal result in this environment — record which was used): confirm the panel renders the correct one of `ready`/`human-review-pending`/`failed` and that the "latest" query is invalidated/refetched exactly once per terminal transition (no duplicate fetch storm).
5. Trigger a `429` (repeat generation past `THESIS_GENERATION_DAILY_LIMIT`, default 5/day): confirm the rate-limited copy shows the correct `limit`/`resetsAt`, not a generic error.
6. Confirm every `evidenceFields` link scrolls to the correct section and that the target section's `id` actually exists on the page (a broken anchor is a merge-blocking defect, not a cosmetic one).
7. Confirm the provenance `<details>` is collapsed by default and expands to show all provenance fields plus valid JSON.
8. As an ADMIN user, open `/admin/thesis-review`: confirm the queue lists any `HUMAN_REVIEW_PENDING` fixture item, the empty state renders when none exist, and clicking a row's symbol lands on `#thesis` on that symbol's review page.
9. As a non-ADMIN user, confirm navigating directly to `/admin/thesis-review` redirects to `/` and the nav link is absent from the sidebar.
10. Resize to a narrow viewport: confirm the panel's lists, badges, and the provenance block don't overflow or overlap (matching the responsive bar every other review-page section already meets).

## Merge Readiness

1. Spec files (`plan.md`, `requirements.md`, `validation.md`) exist and are non-empty.
2. `npm run build` passes with zero errors.
3. `git diff --stat main` touches only `frontend/src/api/thesis.ts`, `frontend/src/components/ThesisPanel.tsx`, `frontend/src/pages/AdminThesisReviewPage.tsx`, `frontend/src/pages/SecurityReviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/components/AppShell.tsx`, this spec directory, and `specs/roadmap.md`'s TA5 status line — nothing under `backend/` or `vis-model-training/`.
4. All manual QA steps above are run and their live-vs-inspection status is recorded honestly in this file before merge (edit this checklist in place with actual results, matching TA4's own validation.md style of recording what was actually run).
5. `specs/roadmap.md` → Phase TA5 marked `*(complete)*` only after the above.

## Known Risks

1. **No automated frontend test coverage** for the new panel's 7-state logic or the admin queue — the project has no test runner; a future phase that adds vitest/RTL to `frontend/` should retrofit coverage here rather than this phase inventing a one-off test setup for itself.
2. **`THESIS_AGENT_ENABLED=false` limits how much of the generating→terminal flow can be verified against a real backend** in this environment; some states may only be verified by code inspection plus a stubbed response until the flag is explicitly turned on in a suitable environment.
3. **The evidence-field → section-anchor map is hand-maintained** in `ThesisPanel.tsx`; if a future phase renames or removes a review-page section id (`#valuation`, `#dividends`, `#debt`, `#earnings`, `#cash`, `#quality`, `#risk`, `#source`), this map must be updated in the same change or a bull/bear case link silently breaks.
4. **Rate-limit UX depends on the 429 body shape TA4 actually returns** (`RATE_LIMIT_EXCEEDED`/`limit`/`resetsAt`, per TA4's `plan.md` → Group 6.2); if the real response differs in field naming, `thesis.ts`'s `RateLimitError` parsing must be corrected against the live response, not assumed from TA4's spec text alone.
