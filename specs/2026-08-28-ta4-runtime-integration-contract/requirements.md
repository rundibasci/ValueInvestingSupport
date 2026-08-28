# TA4 — Runtime Integration Contract & VIS Backend Client

## Context

TA1–TA3 (`specs/2026-08-27-ta1-vertex-gemini-governance/`, `.../ta2-vertex-prompt-contract/`, `.../ta3-vertex-capability-benchmark/`) established the governance decision (ADR-002), the Vertex AI request shape (`vis-model-training/config/vertex-gemini-v1.json`), and the capability-benchmark **GO decision**: `gemini-2.5-flash` clears every applicable field of `config/capability-probe-gate.json` (100% JSON validity/schema compliance across 574 live cases vs. the closed Gemma baseline's 0%, zero genuine semantic-validator violations, zero knowledge leakage, 0.8088 human-review accept rate). TA3 also found and live-verified a one-line prompt fix for a systematic `humanReviewRequired` gap (value-trap pattern) and explicitly deferred adopting it to TA4 (`vis-model-training/reports/vertex/gemini-2.5-flash-v1/experiments/human-review-rule-experiment.md`).

TA4 turns that evidence-backed decision into the actual VIS backend integration: a Java client calling Vertex AI Gemini, persistence for generated theses, three REST endpoints, per-user rate limiting, an ADMIN review queue, a deterministic fallback, and adoption of the TA3 prompt fix with a full corpus re-verification. This phase adapts TRAIN-05/TRAIN-12's runtime-contract design (`vis-model-training/README.md` → "TRAIN-12 — Handoff verso VIS", never actually implemented because it was blocked on TRAIN-05's failed adapter) by repointing every model-identity concept at a pinned `GEMINI_MODEL_ID` instead of an adapter checksum — the envelope, error codes, deterministic-fallback rule, versioning policy, and responsibility matrix all carry over engine-agnostic.

## Scope

### Persistence — `investment_thesis_result`

- New Flyway migration `V27__investment_thesis_result.sql`. Platform-wide reference data (like `ValuationResult`/`ValueScore`, per `specs/tech-stack.md` → Data Model Notes), not user-owned — one row per generation, **never updated in place** (mission.md Principle 6: immutable historical data). A regeneration always inserts a new row; the API serves the latest one plus a computed `stale` flag.
- Columns: `id`, `security_id` (FK), `request_id` (UUID, unique), `requested_by_user_id` (FK, audit only — does not make the row user-owned), `model_id`, `model_version`, `prompt_version`, `input_snapshot` (JSONB — the exact `thesis-input.schema.json`-conforming payload sent to Gemini, for audit/reproducibility), `output_json` (JSONB, nullable on failure), `classification`, `confidence`, `human_review_required` (boolean, nullable on failure), `status` (`GENERATING` / `READY` / `FAILED` / `HUMAN_REVIEW_PENDING`), `error_code`, `error_message`, `raw_output_available` (boolean, per TRAIN-12.2 — never itself served as a substitute for a valid output), `latency_ms`, `generated_at`, `created_at`.
- Indexes: `(security_id, generated_at DESC)` for "latest thesis" lookups; partial index on `status = 'HUMAN_REVIEW_PENDING'` for the review queue; unique on `request_id`.

### `InvestmentThesisClient` (new package `it.mazzoni.vis.thesis`)

- `InvestmentThesisClient` interface: `ThesisGenerationResult generate(ThesisGenerationRequest request)`. One production implementation, `VertexAiInvestmentThesisClient`, calling Vertex AI Gemini via Google Cloud's Java client library (`google-genai`'s Java SDK if it exists and is current at implementation time — reconfirm the exact artifact/package name against current documentation before coding, the same "do not assume TA1's/TA2's Python-side confirmation covers the Java SDK surface" discipline TA2's own `validation.md` already applied to itself).
- Authenticated via Application Default Credentials (ADC) for local/dev — this session's decision, mirroring TA3's approach (no dedicated service account created now). Production (K1+) uses a GCP service account bound to Cloud Run via Secret Manager, per `specs/tech-stack.md` → GCP Distribution — that binding is infrastructure work, out of scope here (see Out of Scope).
- Model/decoding configuration lives in Spring config (`application.yml` + env vars: `GEMINI_MODEL_ID`, `VERTEX_AI_LOCATION`, `GOOGLE_CLOUD_PROJECT`), **not** read from `vis-model-training/config/vertex-gemini-v1.json` directly (separate deployable artifacts, Python training repo vs. Java backend) — but every value (model id, `temperature=0.0`, `thinkingBudget=0`, `maxOutputTokens`, the derived `responseSchema`) must match that file's pinned values, verified by a test that loads both and asserts parity, so the two configs cannot silently drift the way `config/vertex-gemini-v1.json` itself was protected from drifting against `thesis-output.schema.json` in TA2.
- No grounding tools configured (empty/absent) — same defense-in-depth check TA3's `VertexBackend` already enforces in Python, mirrored here.

### Runtime Contract (adapts TRAIN-12, `vis-model-training/README.md` §12.1–12.7)

- Request envelope: `ThesisGenerationRequest(UUID requestId, String modelVersion, ThesisInput input)`.
- Result envelope: sealed interface `ThesisGenerationResult` with `ThesisGenerationSuccess(requestId, status=OK, modelId, modelVersion, promptVersion, latencyMs, ThesisOutput output)` and `ThesisGenerationFailure(requestId, status=ERROR, ThesisErrorCode errorCode, String errorMessage, boolean rawOutputAvailable)`.
- `ThesisErrorCode`: `SCHEMA_VALIDATION_FAILED`, `TIMEOUT`, `INPUT_SCHEMA_INVALID`, `HUMAN_REVIEW_REQUIRED` (per TRAIN-12.2, this is not an error condition — a valid, schema-conforming output with `humanReviewRequired=true`, routed to the review queue, never auto-published as a plain result). TRAIN-12's `MODEL_VERSION_UNAVAILABLE` (an adapter-registry concept — `CANDIDATE`/`APPROVED`/`DEPRECATED` promotion) has no direct equivalent for a single pinned `GEMINI_MODEL_ID`; adapted as a **startup-time configuration failure** (application fails to start if `GEMINI_MODEL_ID` is blank/unset while `THESIS_AGENT_ENABLED=true`), not a runtime error code.
- Retry policy: retry **only** on `SCHEMA_VALIDATION_FAILED` (malformed/non-conforming JSON) or `TIMEOUT` — never to "regenerate a more convincing output" (explicit TRAIN-12 rule, carried over unchanged). Max retries configurable (`THESIS_GENERATION_MAX_RETRIES`, default 1), mandatory deterministic fallback after retries exhausted.
- Deterministic fallback (reconciling TRAIN-12's content rule with `specs/tech-stack.md`'s status enum): on error/timeout/non-conforming output after retries, persist `status=FAILED` with a synthetic output — `classification: UNDER_REVIEW`, `humanReviewRequired: true`, empty `bullCase`/`bearCase`, the tracked error reason in `dataWarnings` — never a silent second-model fallback, never partial/malformed text exposed to a user.
- Every response traceable to `requestId` + `modelId`/`modelVersion` + `promptVersion` for audit (TRAIN-12.3's requirement, unchanged).

### Endpoints (`it.mazzoni.vis.thesis`, mirroring `admin`'s `SeedRunAcceptedResponse`/`SeedRunStatusResponse` async pattern from DL5)

- `POST /api/v1/securities/{symbol}/thesis/generate` — any authenticated role, rate-limited (see below). Starts generation on a virtual-thread executor (Java 21, already in this project's stack per `specs/tech-stack.md`), returns `ThesisGenerationAcceptedResponse(UUID thesisRunId, String status, int pollingIntervalMs, String statusUrl)` immediately (never blocks on Gemini's ~2–5s live latency).
- `GET /api/v1/securities/{symbol}/thesis/runs/{thesisRunId}/status` — polls; `ThesisRunStatusResponse(thesisRunId, status, classification, confidence, humanReviewRequired, errorCode, generatedAt)` while `GENERATING`, full thesis body once terminal (`READY`/`FAILED`/`HUMAN_REVIEW_PENDING`).
- `GET /api/v1/securities/{symbol}/thesis` — latest persisted thesis for the symbol (any of `READY`/`HUMAN_REVIEW_PENDING`/`FAILED`), or a `NOT_GENERATED` marker (never triggers generation itself). Response includes a `stale: boolean` flag, `true` when the underlying `ValuationResult`/`ValueScore` was refreshed after this thesis's `generatedAt`.
- `GET /api/v1/admin/thesis/review-queue` — `ADMIN` only. Lists every thesis with `status=HUMAN_REVIEW_PENDING` **or** non-empty `dataWarnings` (TRAIN-12.5's audit-retention scope, not just the narrower `humanReviewRequired=true` case), newest first, mirroring the existing alert-queue admin pattern (G2) rather than a new subsystem shape.

### Rate Limiting

- `THESIS_GENERATION_DAILY_LIMIT` (default 5, already named in `specs/tech-stack.md`'s environment-variable table) applies **per authenticated user, per calendar day (UTC), to every role** — TA1's explicit decision this session's ADR-002 already recorded: no separate ADMIN-only bulk/batch path is authorized by this decision. Enforced via a Redis counter (`thesis:daily-limit:{userId}:{yyyy-MM-dd}`, TTL to next UTC midnight) — consistent with this project's existing cache-first Redis usage, not a new mechanism. Exceeding the limit returns a clear `429`-equivalent structured error (`RATE_LIMIT_EXCEEDED`, remaining quota reset time), never a generic `500`.

### Adopting TA3's Prompt Fix

TA3 found and live-verified (19-case experiment, 100% reliability where its condition held, zero false positives on controls) that `prompts/system-prompt-v2.txt` Rule 9 never sets `humanReviewRequired=true` for a `STRONGLY_DECLINING` trend alongside a positive margin of safety (a value-trap pattern) — see `vis-model-training/reports/vertex/gemini-2.5-flash-v1/experiments/human-review-rule-experiment.md`. TA3 deliberately deferred adopting it; this session's explicit decision is to adopt it now, as part of TA4:

- **New `prompts/system-prompt-v3.txt`, not an in-place edit of v2.** This project's own established discipline (TA2's `validation.md`: "no in-place edit of v2, only a new v3 file") already anticipated exactly this situation, and `promptVersion` is a tracked, versioned field (`config/vertex-gemini-v1.json`, TRAIN-12.6's semver policy) — an in-place edit would silently invalidate every provenance record TA3 already produced under `promptVersion: system-prompt-v2`.
- `config/vertex-gemini-v1.json`'s `promptPath`/`promptVersion` updated to point at `system-prompt-v3.txt` / `system-prompt-v3`.
- **Full 574-case corpus re-run** against v3 (real spend, ~$0.84 based on TA3's actual per-case token/cost figures) — a new report directory (`reports/vertex/gemini-2.5-flash-v1-promptv3/`), not an overwrite of TA3's `.../gemini-2.5-flash-v1/` evidence (mission.md Principle 6, immutable history — TA3's v1 results stay on record regardless of v3's outcome).
- Re-run TRAIN-02's semantic validator against the new live output (not just schema/classification metrics) — same zero-genuine-violations bar TA3 already cleared.
- **This full re-run and re-verification is a merge-blocking acceptance check for TA4**, not an optional follow-up — the phase must confirm zero regressions elsewhere in the prompt's behavior before the fix (or `THESIS_AGENT_ENABLED=true` at all) can be considered validated.

### Configuration & Test Isolation

- `.env.example` additions: `THESIS_AGENT_ENABLED=false`, `GOOGLE_CLOUD_PROJECT`, `VERTEX_AI_LOCATION`, `GEMINI_MODEL_ID`, `GOOGLE_APPLICATION_CREDENTIALS`, `THESIS_GENERATION_DAILY_LIMIT=5`, `THESIS_GENERATION_MAX_RETRIES=1` — exactly the variable set `specs/tech-stack.md` already documents.
- `.gitignore` rule for the local service-account JSON key file path, added proactively (before any such file is ever created locally), matching the existing `.env`/`**/application-fmpkey.yml` pattern — even though this session uses ADC, not a service-account key, for local dev (per this session's explicit decision), the rule must exist before TA4 ships so a future contributor's key file is never at risk.
- Default backend tests **mock `InvestmentThesisClient`** and never call live Vertex AI. `backend/src/test/resources/application-vertexkey.yml` (gitignored, mirroring `application-fmpkey.yml`) + `@ActiveProfiles({"test","vertexkey"})` for the small set of integration tests that do call the real API — excluded from the default CI run, exactly as `specs/tech-stack.md` already specifies.
- `THESIS_AGENT_ENABLED` stays `false` by default; this phase does not flip it to `true` in any committed config — that is an explicit, separate operational decision after this phase's own acceptance suite (including the v3 corpus re-run) passes.

## Decisions

1. **Full TA4 scope in one phase**, matching how TA1/TA2/TA3 were each treated as a single phase/branch despite substantial content — not split into a TA4a/TA4b.
2. **TA3's prompt fix is adopted within TA4**, not deferred further — as a new `system-prompt-v3.txt` (never an in-place v2 edit) with a mandatory full-corpus re-verification before the fix is considered closed.
3. **Local/dev Vertex AI credentials reuse the existing ADC** (this session's explicit decision) rather than a dedicated service account — matching TA3's precedent. Production service-account/Secret Manager wiring is out of scope (a K-series infrastructure concern).
4. **`investment_thesis_result` is platform-wide reference data**, immutable per row, regeneration always inserts — never an update-in-place, per mission.md Principle 6.
5. **Rate limiting applies uniformly to every authenticated role** — no ADMIN-only bulk path, per TA1's ADR-002 decision this phase must not silently relax.
6. **TRAIN-12's `MODEL_VERSION_UNAVAILABLE` error code has no runtime equivalent** for a single pinned `GEMINI_MODEL_ID` (no adapter-promotion registry exists) — reinterpreted as a startup-time configuration failure, recorded explicitly rather than silently dropped.
7. **The review queue's scope is `HUMAN_REVIEW_PENDING` status OR non-empty `dataWarnings`**, matching TRAIN-12.5's audit-retention policy exactly, not the narrower `humanReviewRequired=true` alone.

## Out of Scope

- Production service-account creation, Secret Manager binding, and Cloud Run IAM wiring for Vertex AI credentials — a K-series (K1+) infrastructure concern, not this phase's backend-code concern.
- Any frontend change (the "Generate AI Thesis" panel, review-queue UI, state badges) — `specs/roadmap.md` → Phase TA5.
- Flipping `THESIS_AGENT_ENABLED=true` in any deployed/committed environment config — an explicit, separate operational decision after this phase's acceptance suite passes.
- Widening TA3's prompt fix to the broader "moderate decline / thin margin" pattern the 19-case experiment deliberately left unresolved — a possible future prompt-tuning iteration, not part of adopting the already-verified fix.
- Changing `thesis-input.schema.json`, `thesis-output.schema.json`, or the derivation logic in `vis_training/vertex/schema_adapter.py` — all carried over unchanged from TA1/TA2/TA3.
- Re-opening the gate-field decisions already resolved in TA3 (`minimumAverageScores`, TRAIN-05 critic fields) — settled, not revisited here.

## Compatibility and Risks

- **Resolved during implementation (2026-08-28): `com.google.genai:google-genai:1.68.0`** — the Java SDK's actual field/method names were confirmed by `javap` against the real downloaded jar, not assumed from the Python precedent or from web-search summaries (one summary source claimed `.enterprise(true)` was the Vertex AI mode flag; `javap` showed the real flag is `.vertexAI(true)`, a separate builder method — the discrepancy that made verifying against the jar itself, not documentation, the right call). `Client.builder().vertexAI(true).project(...).location(...).build()`, `GenerateContentConfig`/`ThinkingConfig`/`Schema` builders, and `GenerateContentResponse.usageMetadata()` all mirror the Python SDK's shape closely.
- **Duplicated model/decoding configuration between the Python training repo and the Java backend** is an accepted, disclosed tradeoff (two separate deployable artifacts) — the parity test (see Scope → `InvestmentThesisClient`) is the mechanism that catches drift, not a promise drift cannot happen.
- **The v3 prompt corpus re-run is real spend** (~$0.84, based on TA3's actual figures) and must be explicitly authorized before the live calls are made, same discipline as TA3's own live run.
- **Rate-limiting via a Redis TTL counter is not perfectly atomic under extreme concurrency** (a classic check-then-increment race at the exact limit boundary) — acceptable for a `THESIS_GENERATION_DAILY_LIMIT` default of 5 per user per day (not a high-throughput path); a stricter atomic Lua-script increment can be a follow-up if real usage ever demands it, not a blocking requirement here.
