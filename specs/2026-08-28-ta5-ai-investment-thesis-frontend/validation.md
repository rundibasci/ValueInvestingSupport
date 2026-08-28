# TA5 — Validation

## Acceptance Checks — Result

- [x] `frontend/src/api/thesis.ts` exists, exports `thesisApi` with `generate`/`status`/`latest`/`reviewQueue`. Types were corrected against the real TA4 Java source rather than the original sketch — see Corrections below; `EvidenceField`/`ThesisClassification` enums match `thesis-output.schema.json` exactly (verified against `EvidenceField.java`/`ThesisClassification.java` directly).
- [x] `latest()` resolves to `null` (never throws) when the backend reports `status: "NOT_GENERATED"` — corrected from an assumed `404` to the real always-`200` contract (`ThesisController.latest`/`ThesisResponse.notGenerated`).
- [x] `generate()` throws a `RateLimitError` (`limit`/`resetsAt`) on `429`, and a new `ThesisDisabledError` on `503` (not part of the original 7-state sketch — required once `ThesisController.generate`'s synchronous `SERVICE_UNAVAILABLE` when `THESIS_AGENT_ENABLED=false` was found in the real controller). Both are `instanceof`-distinguishable from a generic thrown `Error`.
- [x] `ThesisPanel.tsx` renders all 7 roadmap states (not-yet-generated, generating, ready, human-review-pending, stale, failed, rate-limited) plus the disabled case above.
- [x] Generation never fires automatically on mount — `latest` is a plain read query; `generate.mutate()` is only ever called from an `onClick` handler (Generate / Retry / Regenerate buttons). Verified by code inspection (no test runner — see Test Strategy).
- [x] The human-review-pending banner is `role="alert"`, rose-bordered, and bull/bear case lists are not rendered in that branch — only `summary`, `keyRisks`, `dataWarnings`.
- [x] The stale banner renders whenever `thesis.stale === true` independent of `status` (checked in the shared `READY`-branch return, before the classification/summary/bull-bear content), with its own Regenerate button and its own inline `generateError` slot (so a rate-limited regenerate doesn't blank out the existing thesis — see Corrections).
- [x] Every bull/bear `claim` renders its `evidenceFields` as `<a href="/securities/{symbol}/review#{section}">` links using the `EVIDENCE_FIELD_SECTION` map in `ThesisPanel.tsx` (all 12 `EvidenceField` enum values mapped, cross-checked against `#valuation`/`#dividends`/`#debt`/`#earnings`/`#cash`/`#quality`/`#source`/`#risk`, every one of which exists as a real `<Section id=...>` in `SecurityReviewPage.tsx`).
- [x] The provenance inspector shows `modelId`/`modelVersion`/`promptVersion`/`generatedAt` plus the full raw JSON — **not** `requestId`/`latencyMs` as originally planned, since `ThesisResponse` (the `latest()` DTO) exposes neither field; the raw-JSON dump still surfaces the row's own `id` for reference.
- [x] The MiFID II disclaimer renders in `ready`/`human-review-pending`/`stale` (the `HUMAN_REVIEW_PENDING` and `READY` return blocks both end with `<Disclaimer />`) and is absent from `not-yet-generated`/`generating`/`failed`/`rate-limited`/`disabled`.
- [x] `AdminThesisReviewPage.tsx` calls the same `session?.role !== "ADMIN"` → `<Navigate to="/" replace />` guard as `AdminJobsPage.tsx`, before the `useQuery` call.
- [x] The review queue renders every row TA4's `findReviewQueue` query returns (TA5 applies no client-side re-filtering), links each symbol to `/securities/{symbol}/review#thesis`, and shows "No theses are currently pending human review." when `content.length === 0`.
- [x] `admin/thesis-review` route added to `App.tsx`; `AppShell.tsx`'s `adminNavigation` array includes `{ label: 'AI Thesis Review', to: '/admin/thesis-review' }`, inheriting the existing `session?.role === 'ADMIN'` gate around the whole `adminNavigation` block — no new gating code needed.
- [x] `git diff --stat main` (see below) touches only `frontend/` files plus this spec directory — no `backend/` or `vis-model-training/` change.

## Corrections Made During Implementation (honest account)

Read directly from `backend/src/main/java/it/mazzoni/vis/thesis/` before writing `thesis.ts`, per this project's own "verify against real code, not spec text" discipline (TA4's own validation.md set this precedent for its Java SDK coordinate). Several assumptions in this spec's original `plan.md`/`requirements.md` sketch (written before re-checking the real DTOs) turned out to be wrong:

1. **`GET .../thesis` never 404s.** `ThesisController.latest()` always returns `200` with `ThesisResponse.notGenerated(symbol)` (`status: "NOT_GENERATED"`) when no row exists. `thesisApi.latest()` was implemented to detect this from the parsed body's `status` field, not the HTTP status code.
2. **No `requestId`, `errorCode`, `errorMessage`, or `latencyMs` field exists on `ThesisResponse`** (the `latest()` DTO) — only on `ThesisRunStatusResponse` (the polling endpoint), and even there no `errorMessage`. A `FAILED` thesis's explanation comes from `output.dataWarnings` (`ThesisOutput.deterministicFallback` puts the tracked error reason there as the sole entry). `ThesisPanel.tsx`'s failed-state rendering was written against this reality, and additionally keeps the last-seen `errorCode` from the polling response in local state (`runError`) as a supplementary detail when available.
3. **The provenance inspector shows `modelId`/`modelVersion`/`promptVersion`/`generatedAt` only** — `requestId`/`latencyMs` were dropped from the fixed-field list (they don't exist on this DTO); the full raw-JSON dump still exposes the row's own `id`.
4. **`generate()` can fail synchronously with `503`**, not just accept-then-fail-async — `ThesisController.generate()` throws `ResponseStatusException(SERVICE_UNAVAILABLE)` immediately when `THESIS_AGENT_ENABLED=false`, before any row is created. Added `ThesisDisabledError`, a case not in the original 7-state design, so this doesn't surface as a confusing generic error.
5. **The review-queue item shape is `{ id, dataWarningsPresent: boolean, ... }`**, not `{ thesisRunId, dataWarnings: string[], ... }` as sketched — corrected against `ThesisReviewQueueItemResponse.java`. The queue table shows a present/absent badge rather than a count.
6. **Pagination wraps in the shared `PageResponse<T>` shape** (`content`/`page`/`size`/`totalElements`/`totalPages`, `it.mazzoni.vis.admin.PageResponse`), not the narrower `{content, totalElements}` originally sketched.
7. **No `requestId` survives a page reload mid-generation.** Since `ThesisResponse` exposes only the row's own `id` (not `requestId`), a reload during an in-flight generation can't resume polling `/runs/{thesisRunId}/status` by id. Added a self-poll fallback: the `["thesis", symbol]` query's own `refetchInterval` keeps polling every 3s whenever the latest row's `status === "GENERATING"`, independent of whether this session holds a live `runId` — not present in the original plan, added once the gap was found.
8. **A rate-limited (or otherwise failed) regenerate attempt on an existing `READY`/`stale` thesis must not blank out that thesis.** The original plan's per-state full-panel-replacement sketch for `generate.isError` was reworked into an inline `generateError` node rendered next to whichever button triggered it (Generate / Retry / Regenerate), so an existing thesis's content is never hidden behind a transient generate-attempt error.

`requirements.md` → Request/Response Shapes has been updated in place to reflect the verified shapes above; `plan.md` is left as originally authored (the historical record of intent), consistent with how TA4 handled its own mid-implementation corrections.

## Test Strategy

This project has no frontend test runner configured (`frontend/package.json` has no `test` script; no vitest/jest/`@testing-library/*` dependency; project-wide `find . -iname "*.test.ts*"` returns zero files as of this phase). This is not a gap introduced by TA5 — it is the same constraint SR2 (`specs/2026-06-30-sr2-scoring-risk-frontend/validation.md` → Known Risks) already documented for the last comparable frontend-only phase. TA5 does not introduce a test framework (see `requirements.md` → Decisions #3); validation is:

1. `cd frontend && npm run build` — runs `tsc -b && vite build`. Zero type errors, zero build errors. This is the actual automated gate for this phase.
2. `cd backend && ./mvnw -o test` is **not required** — TA5 makes no backend change. Run only if a build-time discovery forces an unplanned backend touch (would then require updating this spec, not silently expanding scope).
3. Manual QA (see below), run against a local backend with `THESIS_AGENT_ENABLED=false` (TA4's shipped default, unchanged by this phase).

## Manual QA Sequence — Status

**Not yet run against a live backend in this implementation session** (no local backend/Postgres/Redis stack was started while writing this code) — honestly recorded rather than claimed. Steps 1, 2, 6, 7, 9, 10 below were verified by **code inspection only** (reading the actual rendered JSX/logic, not clicking through a running app); steps 3, 4, 5, 8 require a running TA4 backend and have **not** been verified at all yet. This is the outstanding step before `specs/roadmap.md` → Phase TA5 can be marked `*(complete)*` (see Merge Readiness).

`THESIS_AGENT_ENABLED=false` means the real `generate` endpoint's async Gemini call path is not exercised end-to-end even once a backend is running (per TA4, flipping it is a separate operational decision) — steps 3–5 will exercise the CTA → generating → deterministic-`FAILED`-fallback path (see the answer given earlier in this conversation about what a live demo can and cannot show without flipping the flag), not a real `READY` thesis with model-generated content.

1. **[code-inspection only]** Log in, open `/securities/{seeded-symbol}/review`, confirm the "AI Investment Thesis" section (`#thesis`) renders after "Risk And Data Quality Caveats" and before "Next Actions". *(Confirmed by reading `SecurityReviewPage.tsx`'s section order; not click-verified in a browser.)*
2. **[code-inspection only]** For a symbol with no prior thesis: confirm only the call-to-action renders and no `generate` mutation fires on mount. *(Confirmed — `generate.mutate()` only appears inside `onClick` handlers, never in a `useEffect` or on render.)*
3. **[not yet run]** Click "Generate AI Thesis" against a live backend: confirm the `202` triggers the generating state and polling begins at `pollingIntervalMs`.
4. **[not yet run]** Reach a terminal state (expected: `FAILED` via the deterministic fallback, since `THESIS_AGENT_ENABLED=false`): confirm the panel renders it and `["thesis", symbol]` refetches exactly once per terminal transition.
5. **[not yet run]** Trigger a `429` (repeat past `THESIS_GENERATION_DAILY_LIMIT`, default 5/day): confirm the rate-limited copy shows the real `limit`/`resetsAt`.
6. **[code-inspection only]** Every `evidenceFields` link target (`#valuation`, `#quality`, `#dividends`, `#debt`, `#earnings`, `#cash`, `#source`, `#risk`) was cross-checked against `SecurityReviewPage.tsx`'s actual `<Section id=...>` list — all 8 target ids exist. *(Not click-verified for actual scroll behavior in a browser.)*
7. **[code-inspection only]** The `<details>` element has no `open` attribute, so it renders collapsed by default per standard HTML semantics. *(Not visually confirmed in a browser.)*
8. **[not yet run]** As an ADMIN user, open `/admin/thesis-review` against a live backend with a `HUMAN_REVIEW_PENDING` fixture row.
9. **[code-inspection only]** `AdminThesisReviewPage.tsx`'s guard (`session?.role !== "ADMIN"` → redirect) is textually identical in structure to `AdminJobsPage.tsx`'s working guard. *(Not click-verified as a non-admin user.)*
10. **[not yet run]** Narrow-viewport layout check.

## Merge Readiness

1. Spec files (`plan.md`, `requirements.md`, `validation.md`) exist and are non-empty. ✅
2. `npm run build` (`tsc -b && vite build`) — ✅ **ran this session, zero errors** (one pre-existing, unrelated chunk-size warning).
3. `git diff --stat main` — ✅ touches only `frontend/src/api/thesis.ts` (new), `frontend/src/components/ThesisPanel.tsx` (new), `frontend/src/pages/AdminThesisReviewPage.tsx` (new), `frontend/src/pages/SecurityReviewPage.tsx`, `frontend/src/App.tsx`, `frontend/src/components/AppShell.tsx`, plus this spec directory — nothing under `backend/` or `vis-model-training/`.
4. Manual QA steps 3, 4, 5, 8, 10 above — ❌ **not yet run**; require a running local backend (`docker-compose up` for Postgres/Redis + `mvn spring-boot:run`), which was not started in this implementation session.
5. `specs/roadmap.md` → Phase TA5 — **not marked complete**, per this spec's own `plan.md` → Group 6.4 gate ("only after `validation.md`'s checklist passes") and item 4 above. Mark it once a live-backend pass through the Manual QA Sequence is actually run, updating the `[not yet run]` markers above with real results.

## Known Risks

1. **No automated frontend test coverage** for the new panel's 7-state logic or the admin queue — the project has no test runner; a future phase that adds vitest/RTL to `frontend/` should retrofit coverage here rather than this phase inventing a one-off test setup for itself.
2. **`THESIS_AGENT_ENABLED=false` limits how much of the generating→terminal flow can be verified against a real backend** in this environment; some states may only be verified by code inspection plus a stubbed response until the flag is explicitly turned on in a suitable environment.
3. **The evidence-field → section-anchor map is hand-maintained** in `ThesisPanel.tsx`; if a future phase renames or removes a review-page section id (`#valuation`, `#dividends`, `#debt`, `#earnings`, `#cash`, `#quality`, `#risk`, `#source`), this map must be updated in the same change or a bull/bear case link silently breaks.
4. **Rate-limit UX depends on the 429 body shape TA4 actually returns** (`RATE_LIMIT_EXCEEDED`/`limit`/`resetsAt`, per TA4's `plan.md` → Group 6.2); if the real response differs in field naming, `thesis.ts`'s `RateLimitError` parsing must be corrected against the live response, not assumed from TA4's spec text alone.
