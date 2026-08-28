# TA5 — Implementation Plan

## 1. `frontend/src/api/thesis.ts` — typed client

1. Add types mirroring TA4's DTOs (`backend` → `it.mazzoni.vis.thesis`), matching `thesis-output.schema.json` field-for-field:
   - `ThesisClassification = 'POTENTIALLY_UNDERVALUED' | 'FAIRLY_VALUED' | 'POTENTIALLY_OVERVALUED' | 'UNDER_REVIEW' | 'INSUFFICIENT_DATA'`
   - `EvidenceField = 'marketPrice' | 'intrinsicValue' | 'marginOfSafetyPercent' | 'valueScore' | 'dividendYieldPercent' | 'payoutRatioPercent' | 'netDebtToEbitda' | 'revenueTrend' | 'earningsTrend' | 'freeCashFlowTrend' | 'dataQuality' | 'deterministicWarnings'`
   - `ThesisEvidence = { claim: string; evidenceFields: EvidenceField[] }`
   - `ThesisStatus = 'GENERATING' | 'READY' | 'FAILED' | 'HUMAN_REVIEW_PENDING'`
   - `Thesis = { thesisRunId: string; requestId: string; status: ThesisStatus; classification: ThesisClassification | null; confidence: number | null; summary: string | null; bullCase: ThesisEvidence[]; bearCase: ThesisEvidence[]; keyRisks: string[]; keyAssumptions: string[]; invalidationConditions: string[]; dataWarnings: string[]; humanReviewRequired: boolean; errorCode: string | null; errorMessage: string | null; modelId: string | null; modelVersion: string | null; promptVersion: string | null; latencyMs: number | null; generatedAt: string | null; stale: boolean }`
   - `ThesisGenerationAccepted = { thesisRunId: string; status: string; pollingIntervalMs: number; statusUrl: string }`
   - `ThesisRunStatus = { thesisRunId: string; status: ThesisStatus; classification: ThesisClassification | null; confidence: number | null; humanReviewRequired: boolean | null; errorCode: string | null; generatedAt: string | null }`
   - `ThesisRateLimitError = { code: 'RATE_LIMIT_EXCEEDED'; limit: number; resetsAt: string }` — a typed shape for the 429 body, not a generic `Error`.
   - `ReviewQueueItem = { thesisRunId: string; symbol: string; companyName: string | null; classification: ThesisClassification | null; humanReviewRequired: boolean; dataWarnings: string[]; generatedAt: string | null }`
2. Reuse the `json<T>()`/`body()` helpers already established in `watchlist.ts` (do not duplicate — extract to `./client.ts` only if a third API file needs the exact same helpers verbatim during this phase; otherwise copy the two small functions as every existing API file already does).
3. `thesisApi` object:
   - `generate(symbol: string): Promise<ThesisGenerationAccepted>` — `POST /api/v1/securities/{symbol}/thesis/generate`. On a `429` response, parse the JSON body and `throw` a `RateLimitError extends Error` carrying `{ limit, resetsAt }` (a distinguishable error subtype the panel can branch on with `instanceof`), rather than the generic `Request failed (429).` every other API file throws today.
   - `status(symbol: string, thesisRunId: string): Promise<ThesisRunStatus>` — `GET /api/v1/securities/{symbol}/thesis/runs/{thesisRunId}/status`.
   - `latest(symbol: string): Promise<Thesis | null>` — `GET /api/v1/securities/{symbol}/thesis`; returns `null` on a `404`/`NOT_GENERATED` body instead of throwing (mirrors how `latest()`-style "may not exist yet" reads are handled elsewhere — check `portfolioAnalysis.ts` for the closest existing precedent to this exact null-vs-throw split before diverging from it).
   - `reviewQueue(page = 0, pageSize = 20): Promise<{ content: ReviewQueueItem[]; totalElements: number }>` — `GET /api/v1/admin/thesis/review-queue?page=&size=`.
4. No test file — this project has no test runner configured (`package.json` has no `test` script, no vitest/jest dependency; confirmed by `find . -iname "*.test.ts*"` returning zero results project-wide). Match every existing `src/api/*.ts` file's precedent: typed, untested, exercised through the component and manual QA below. Do not introduce a test framework as a side effect of this phase — that is a separate, explicit decision if ever made (see `requirements.md` → Decisions).

## 2. `frontend/src/components/ThesisPanel.tsx` — the Security Review page panel

1. Props: `{ symbol: string }`. Mounted from `SecurityReviewPage.tsx` inside a new `<Section id="thesis" title="AI Investment Thesis">` (reusing the page's existing `Section` helper component, matching the `id`/`title` convention already used by `#valuation`, `#dividends`, `#debt`, `#quality`, `#risk`, `#source`), placed directly after the `#risk` section and before `#actions` so the interpretive layer appears after every deterministic section it references.
2. State machine driven by TanStack Query, following `SeedRunProgress.tsx`'s exact polling pattern:
   - `latest = useQuery({ queryKey: ['thesis', symbol], queryFn: () => thesisApi.latest(symbol) })` — drives the **not-yet-generated** (`latest.data === null`), **ready** (`status === 'READY'`), **human-review-pending** (`status === 'HUMAN_REVIEW_PENDING'`), **stale** (`latest.data?.stale === true`, checked independently of status — a `READY` thesis can also be `stale`), and **failed** (`status === 'FAILED'`) states.
   - `generate = useMutation({ mutationFn: () => thesisApi.generate(symbol), onSuccess: (accepted) => setRunId(accepted.thesisRunId) })` — drives transition into **generating**. On error, `if (error instanceof RateLimitError)` renders the **rate-limited** state with `error.resetsAt`; any other error renders a generic retry affordance (do not conflate the two — a rate limit is not a failure to retry immediately).
   - `runStatus = useQuery({ queryKey: ['thesis-run', symbol, runId], queryFn: () => thesisApi.status(symbol, runId!), enabled: Boolean(runId), refetchInterval: (query) => query.state.data && query.state.data.status !== 'GENERATING' ? false : 1500 })` — drives the **generating** progress indicator; on reaching a terminal status, `useEffect` invalidates `['thesis', symbol]` so `latest` refetches the full body instead of duplicating field-by-field mapping from the narrower status DTO.
3. Render function per state (a single `switch` on a derived `PanelState` union, not nested ternaries — matching the readability bar of the existing `Section`/`Metric` helpers in `SecurityReviewPage.tsx`):
   - **not-yet-generated**: call-to-action copy + `<button onClick={() => generate.mutate()}>Generate AI Thesis</button>`, disabled while `generate.isPending`.
   - **generating**: reuse `SeedRunProgress.tsx`'s `role="status"` text-progress convention (no percentage bar here — there is no `processed/total` count for a single-call generation, just an indeterminate "Generating…" state) plus the elapsed-time text already used elsewhere (`Run {runId}` equivalent).
   - **ready**: classification badge (color-coded per `ThesisClassification`, reusing the page's existing MoS-badge color convention — green/amber/rose — do not invent a fourth palette), `confidence` as a percentage, `summary`, two `<ul>` lists for `bullCase`/`bearCase` where each `<li>` renders `claim` followed by its `evidenceFields` as inline `<Link>`s to `#<section-id>` anchors on the same page (mapping table below), `keyRisks`/`keyAssumptions`/`invalidationConditions` as plain lists, `dataWarnings` (if non-empty) as amber notices.
   - **human-review-pending**: an unmissable banner (`role="alert"`, rose/amber border matching the page's existing alert styling, e.g. `border-rose-300/30 bg-rose-400/10`) reading "This thesis is flagged for human review — do not treat it as a finished recommendation" **rendered instead of** the bull/bear case list, not alongside it; only `summary`, `keyRisks`, and `dataWarnings` are shown underneath for context.
   - **stale**: a dismissible-by-scroll (non-blocking) banner above the otherwise-normal `ready` rendering: "This thesis was generated before the latest valuation/score refresh." + a "Regenerate" button calling `generate.mutate()` again.
   - **failed**: `errorMessage`/`errorCode` shown plainly (never the raw Gemini output — TA4's contract never returns one on `FAILED`) + a "Retry" button.
   - **rate-limited**: "Daily thesis generation limit reached ({limit}/day). Try again after {resetsAt}." — never the generic error copy used for other failures.
4. `evidenceFields` → review-page section anchor map (module-level `const`, exported for the admin queue page's future reuse if ever needed):
   ```
   marketPrice → #valuation
   intrinsicValue → #valuation
   marginOfSafetyPercent → #valuation
   valueScore → #quality
   dividendYieldPercent → #dividends
   payoutRatioPercent → #dividends
   netDebtToEbitda → #debt
   revenueTrend → #earnings
   earningsTrend → #earnings
   freeCashFlowTrend → #cash
   dataQuality → #source
   deterministicWarnings → #risk
   ```
5. Provenance inspector: a plain `<details><summary>Model & prompt details</summary>` block (no new dependency — this is the same mechanism the FD1/PFD1 static demo pages already use for their raw-JSON inspectors, adapted to JSX) showing `modelId`, `modelVersion`, `promptVersion`, `generatedAt`, `requestId`, `latencyMs`, plus a `<pre>{JSON.stringify(latest.data, null, 2)}</pre>` for the full raw payload — rendered in every terminal state (`ready`/`human-review-pending`/`stale`/`failed`), not only `ready`.
6. MiFID II disclaimer: reuse the exact fallback string already used at `SecurityReviewPage.tsx:1059` (`'This is a decision-support tool, not investment advice (MiFID II).'`) styled with the same `border-amber-300/20 bg-amber-300/5 text-amber-100` classes, rendered in every state that shows any thesis content (`ready`, `human-review-pending`, `stale`) — never in `not-yet-generated`, `generating`, `failed`, or `rate-limited`, where there is no thesis content to disclaim.

## 3. Mount the panel

1. `SecurityReviewPage.tsx`: import `ThesisPanel`, add `<Section id="thesis" title="AI Investment Thesis">` wrapping `<ThesisPanel symbol={symbol} />` after the `#risk` section (see `plan.md` → Group 2.1 for exact placement), and add `"thesis"` to whatever anchor/table-of-contents list the page already renders for its section ids, if one exists (check for a nav/TOC element referencing `#valuation`, `#risk`, etc. before assuming one does or doesn't).

## 4. `frontend/src/pages/AdminThesisReviewPage.tsx` — ADMIN review queue

1. Follow `AdminJobsPage.tsx`'s exact shape: `const { session } = useAuth(); if (session?.role !== 'ADMIN') return <Navigate to="/" replace />;` before any data fetching.
2. `useQuery({ queryKey: ['thesis-review-queue', page], queryFn: () => thesisApi.reviewQueue(page) })`; a simple table — symbol (linking to `/securities/{symbol}/review#thesis`), company name, classification, `humanReviewRequired` badge, `dataWarnings` count, `generatedAt` — with page-forward/back controls if `totalElements` exceeds one page.
3. Empty state: "No theses are currently pending human review." (not a blank table).
4. Row's symbol link uses the `#thesis` anchor so triaging an item lands directly on the panel, not just the top of the review page.

## 5. Routing and navigation

1. `App.tsx`: import `AdminThesisReviewPage`, add `<Route path="admin/thesis-review" element={<AdminThesisReviewPage />} />` alongside the other `admin/*` routes.
2. `AppShell.tsx`: add `{ label: 'AI Thesis Review', to: '/admin/thesis-review' }` to the existing `adminNavigation` array (already gated behind `session?.role === 'ADMIN'` — no new gating logic needed).

## 6. Validation, build, and merge

1. `cd frontend && npm run build` (runs `tsc -b && vite build`) — zero type errors, zero build errors. This is the project's only automated frontend gate today (see Group 1 step 4 — no test runner exists); do not claim component-test coverage that was not actually run.
2. Manual QA against the running backend with `THESIS_AGENT_ENABLED=false` left untouched (per TA4, flipping it is a separate operational decision — TA5 never flips it): exercise not-yet-generated → generating → terminal state using a mocked/stubbed backend response if `THESIS_AGENT_ENABLED=false` makes the real generate path unreachable end-to-end; document in `validation.md` exactly which states were verified against a live TA4 backend versus verified by code inspection only, honestly, per this project's own precedent (`specs/2026-06-30-sr2-scoring-risk-frontend/validation.md` → Known Risks already sets this precedent for a test-runner-less frontend).
3. `git diff --stat main` reviewed to confirm only `frontend/` files changed (plus this spec directory and `specs/roadmap.md`'s TA5 status line) — no accidental `backend/` or `vis-model-training/` edits, since TA5 introduces no new backend contract.
4. Update `specs/roadmap.md` → Phase TA5 to `*(complete)*` only after `validation.md`'s checklist passes.
