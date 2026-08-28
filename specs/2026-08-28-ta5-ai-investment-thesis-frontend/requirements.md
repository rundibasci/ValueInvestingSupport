# TA5 — AI Investment Thesis Frontend

## Context

TA4 (`specs/2026-08-28-ta4-runtime-integration-contract/`) shipped the full backend contract this phase consumes unchanged: three symbol-scoped endpoints (`POST .../thesis/generate`, `GET .../thesis/runs/{id}/status`, `GET .../thesis`) plus an ADMIN-only `GET /api/v1/admin/thesis/review-queue`, backed by the immutable `investment_thesis_result` table. `THESIS_AGENT_ENABLED` stays `false` in every environment TA4 touched — flipping it is an explicit, separate operational decision that neither TA4 nor TA5 makes. TA5 introduces **no backend change**: it is a pure frontend consumer of TA4's already-tested contract, matching how `specs/roadmap.md` scopes it ("Add an 'AI Investment Thesis' panel... Add an ADMIN-only review queue view").

The frontend (`frontend/`) is a React 18 + TypeScript 5 + Vite + TanStack Query + Tailwind app (`specs/tech-stack.md` → Frontend). `SecurityReviewPage.tsx` (1224 lines) is the existing single-file review page with inline types and section-by-section rendering via a shared `Section`/`Metric` helper pair; `PortfolioAnalysisPanel.tsx` and `SeedRunProgress.tsx` are the existing precedent for extracting an async, polling, multi-state panel into its own component file rather than growing the review page further. `SeedRunProgress.tsx` in particular is the closest structural analog to this phase's problem (start an async run, get a `runId`-shaped accepted response, poll a status endpoint, land on a terminal state) and is reused as the polling template.

**Frontend has no test runner today.** `frontend/package.json` has no `test` script and no test-framework dependency (vitest, jest, `@testing-library/*` are all absent); a project-wide `find . -iname "*.test.ts*"` returns zero files. The most recent comparable frontend-only phase, SR2 (`specs/2026-06-30-sr2-scoring-risk-frontend/`), documented this exact constraint as a "Known Risk" and validated via `npm run build` + manual QA only. TA5 follows the same precedent rather than silently assuming a test framework exists — see Decisions below for what this changes relative to the phase's own initial framing.

## Scope

| In scope | Out of scope |
|---|---|
| `frontend/src/api/thesis.ts` typed client for all 4 TA4 endpoints | Any backend endpoint, migration, or contract change (TA4 already shipped these) |
| `ThesisPanel.tsx` mounted on the Security Review page, all 7 states from `specs/roadmap.md` TA5 | Flipping `THESIS_AGENT_ENABLED` in any environment |
| Provenance inspector (model/prompt/requestId/generatedAt, collapsible raw JSON) | Adding a test framework (vitest/RTL) to the frontend project |
| MiFID II disclaimer alongside thesis content | Bulk/batch thesis generation UI (TA4 explicitly scoped this out; not implied here either) |
| ADMIN-only `AdminThesisReviewPage.tsx` + `admin/thesis-review` route + nav link | Editing or acting on a thesis from the review queue beyond linking to its symbol/panel (no approve/reject workflow — TA4's contract has no such endpoint) |
| Evidence-field → review-page-section anchor links in bull/bear case lists | Redesigning the review page's existing sections or their ids |

## Decisions

1. **New standalone component, not inline in `SecurityReviewPage.tsx`.** The review page is already 1224 lines with no per-feature extraction discipline broken yet except for genuinely separate concerns (`PortfolioAnalysisPanel.tsx`); adding ~7-state panel logic inline would make an already-large file materially harder to navigate. `ThesisPanel.tsx` takes a single `symbol` prop and owns its own React Query state, matching `SeedRunProgress.tsx`'s existing shape.
2. **Reuse `SeedRunProgress.tsx`'s poll-until-terminal pattern exactly** (`refetchInterval` returning `false` once a terminal status is reached, then invalidating the "latest" query) rather than inventing a new polling primitive — this codebase already has exactly one async-run-with-status-polling pattern (DL5's seed runs) and TA4's generate/status/latest triad was explicitly designed to mirror `SeedRunAcceptedResponse`/`SeedRunStatusResponse` (see TA4's `plan.md` → Group 5), so the frontend precedent should mirror it too.
3. **No test framework introduced.** Given the project has zero test infrastructure today and this is a strictly additive frontend phase with no new backend risk surface, adding vitest/RTL is a separate, larger decision than this phase's stated scope — matching SR2's precedent rather than quietly expanding scope. Validation is `npm run build` (the project's real automated gate: `tsc -b && vite build`) plus documented manual QA, exactly as SR2 already did.
4. **`Thesis`/`ThesisEvidence`/etc. types are hand-mirrored from `thesis-output.schema.json`**, not generated — this project has no OpenAPI/schema-to-TS codegen step anywhere in `frontend/`; every existing `src/api/*.ts` file hand-writes its response types the same way.
5. **A `RateLimitError` subclass, not a generic thrown `Error`,** is the one deliberate deviation from `watchlist.ts`'s minimal `json()`/error-message pattern — the rate-limited state needs `limit`/`resetsAt` to render its specific copy, which a string message alone can't carry cleanly. This is scoped to `thesis.ts` only; no other API file is touched or asked to adopt the same pattern.
6. **`latest()` returns `null` on `NOT_GENERATED` instead of throwing.** A security with no thesis yet is an expected, common state (every seeded symbol starts here), not an error condition — the panel's `not-yet-generated` state is the default render path, not an error boundary.
7. **The evidence-field → section-anchor map lives in `ThesisPanel.tsx`**, not in a shared lib file — it is single-purpose to this panel's bull/bear case rendering and has no other consumer yet; move it to `src/lib/` only if the admin review-queue page or a future surface needs the same mapping.
8. **Placement: after `#risk`, before `#actions`.** The thesis panel interprets deterministic outputs from every section above it (valuation, dividends, debt, earnings, cash, quality/score, risk); placing it after all of them and before the page's final "Next Actions" section keeps the interpretive layer visually subordinate to the computed facts it references, consistent with mission.md Principle 15 ("AI-assisted thesis synthesis is interpretation, not computation").

## Request/Response Shapes (consumed, not introduced)

```ts
// POST /api/v1/securities/{symbol}/thesis/generate → 202
{ thesisRunId: string; status: string; pollingIntervalMs: number; statusUrl: string }

// 429 body when THESIS_GENERATION_DAILY_LIMIT is exceeded
{ code: 'RATE_LIMIT_EXCEEDED'; limit: number; resetsAt: string }

// GET .../thesis/runs/{thesisRunId}/status
{ thesisRunId: string; status: 'GENERATING'|'READY'|'FAILED'|'HUMAN_REVIEW_PENDING';
  classification: string | null; confidence: number | null;
  humanReviewRequired: boolean | null; errorCode: string | null; generatedAt: string | null }

// GET .../thesis → latest, or a NOT_GENERATED marker mapped to `null` by the client
{ thesisRunId, requestId, status, classification, confidence, summary,
  bullCase: [{ claim: string; evidenceFields: string[] }],
  bearCase: [{ claim: string; evidenceFields: string[] }],
  keyRisks: string[]; keyAssumptions: string[]; invalidationConditions: string[];
  dataWarnings: string[]; humanReviewRequired: boolean;
  errorCode, errorMessage, modelId, modelVersion, promptVersion,
  latencyMs, generatedAt, stale: boolean }

// GET /api/v1/admin/thesis/review-queue?page=&size=
{ content: [{ thesisRunId, symbol, companyName, classification,
               humanReviewRequired, dataWarnings: string[], generatedAt }],
  totalElements: number }
```

`classification` values: `POTENTIALLY_UNDERVALUED | FAIRLY_VALUED | POTENTIALLY_OVERVALUED | UNDER_REVIEW | INSUFFICIENT_DATA` (exactly `thesis-output.schema.json`'s enum — `UNDER_REVIEW` is also TA4's deterministic-fallback classification on `FAILED`, so the panel must not assume `UNDER_REVIEW` implies success).

## Out of Scope

- Any change to `backend/` — TA4's endpoints, DTOs, and persistence are consumed as-is.
- Flipping `THESIS_AGENT_ENABLED` anywhere.
- Introducing a frontend test framework (vitest/RTL) — a separate decision, not bundled into this phase.
- Approve/reject/dismiss actions on the admin review queue — read-only triage list only, per TA4's actual endpoint surface.
- Bulk/batch generation UI (explicitly out of scope in TA4's own spec; not reintroduced here).
- Redesigning `SecurityReviewPage.tsx`'s existing sections, `Section`/`Metric` helpers, or badge color conventions beyond reusing them.
