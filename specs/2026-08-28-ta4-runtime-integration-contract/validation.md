# TA4 — Validation

## Acceptance Checks — Result

- [x] `V27__investment_thesis_result.sql` applies cleanly (verified via Hibernate's own DDL generation matching the entity in every `@DataJpaTest`, 495+3 test runs). No update/save-over-existing-id path exists in `InvestmentThesisResultRepository` or anywhere the row is written (`ThesisGenerationService` only ever inserts a new row per generation).
- [x] `InvestmentThesisClient`/`VertexAiInvestmentThesisClient` exist; every default test injects a fake `GeminiCaller` — `grep -rn "GOOGLE_APPLICATION_CREDENTIALS\|vertexai\." backend/src/test/` (excluding `ThesisRateLimiterIT`, which needs Redis not Vertex AI) returns nothing live-API-related.
- [x] `ThesisConfigParityTest` passes (2/2): decoding constants and the hand-built `ThesisResponseSchema` are structurally equivalent (order-independent, matching TA2's own `assert_schema_equivalent` approach) to the checked-in `vertex-gemini-v1-fixture.json`.
- [x] All three endpoints (`generate`/`status`/`latest`) and the admin review-queue endpoint exist, return the exact response shapes in `plan.md` → Group 5, and rely on `SecurityConfig`'s existing `/api/v1/admin/**` → `ROLE_ADMIN` path matcher (no new security annotation needed).
- [x] Deterministic fallback verified by `ThesisGenerationServiceTest::runGeneration_persistsFailed_withDeterministicFallbackBody_onFailure`: `status=FAILED`, `classification=UNDER_REVIEW`, `humanReviewRequired=true`, empty bull/bear case, tracked error reason.
- [x] Rate limiting verified against **real Redis via Testcontainers** (`ThesisRateLimiterIT`, 3/3, run this session with Docker available) — Nth+1 request rejected, independent per-user quotas, TTL set on first increment.
- [x] Review queue query (`findReviewQueue`) verified by `InvestmentThesisResultRepositoryTest` to include `HUMAN_REVIEW_PENDING` **or** `dataWarningsPresent=true` rows and exclude plain `READY` rows.
- [x] `THESIS_AGENT_ENABLED` defaults `false` in `application.yml`/`.env.example`; never flipped `true` anywhere in this phase's diff.
- [x] `prompts/system-prompt-v3.txt` exists; `prompts/system-prompt-v2.txt` is byte-identical to `main` (confirmed — the one in-place edit this session made to it was caught by TRAIN-03's own drift test and reverted before commit, see Known Risks/Corrections below). `config/vertex-gemini-v1.json` points `promptPath`/`promptVersion` at v3.
- [x] **Full 574-case corpus re-run against v3 complete**: `results/vertex-gemini-2.5-flash-v3/`, 0 generation/parse errors. `humanReviewRequired` fires **63/63 (100%)** on every TRAIN-04 case matching the fix's exact condition (`STRONGLY_DECLINING` + positive margin, not already data-quality-flagged) — up from 0/68 on v2 (TA3). TRAIN-02 semantic validator run against the real v3 output: 18 mechanical flags across 574 cases, all manually verified as false positives or a single minor borderline case (same severity class TA3's v1 report already documented) — zero genuine violations. Full writeup: `reports/vertex/gemini-2.5-flash-v1-promptv3/error-analysis.md`.
- [x] `.env.example` updated with the 7 new variables (root-level `.env.example`, not `backend/.env.example` — corrected from `plan.md`'s assumption once the real file location was checked). `.gitignore` already had `**/application-vertexkey.yml`/`**/*service-account*.json`/`**/gcp-credentials*.json` rules from an earlier K-series phase — nothing new needed.
- [ ] `specs/roadmap.md` → Phase TA4 — **not yet marked**; pending final review of this validation document.

## Corrections Made During Implementation (honest account)

1. **JSONB review-queue predicate risk (flagged in the original plan) — resolved by design change, not by testing around it.** Added a plain `data_warnings_present BOOLEAN` column instead of a JSONB-emptiness partial-index predicate — sidesteps the Postgres JSONB semantics risk entirely rather than depending on it working correctly.
2. **`ThesisInputBuilder`/`ThesisGenerationService` seam**: `ThesisInputBuilder` (the real MarketDataClient-backed implementation) was split behind a new `ThesisInputSource` interface once it became clear the concrete class (with its repository/client constructor dependencies) couldn't be used directly as a test double.
3. **A real execution mistake, caught and corrected before this document was finalized**: the first attempt at the Group 7 corpus re-run silently reused `system-prompt-v2.txt`'s text under a `v3` label (~$0.84 wasted) — `VertexBackend.generate()` reads the system prompt from each dataset record's own embedded `messages[0]`, never from `config/vertex-gemini-v1.json`'s `promptPath` at call time; updating the config alone was not sufficient, and this plan's own Group 7 step 3 had flagged exactly this risk without actually checking it before spending. Corrected by regenerating `scenarios-benchmark-v1.jsonl`/`real-ticker-knowledge-leakage-v1.jsonl` with v3 embedded, and creating a new `base-benchmark-v1-promptv3.jsonl` variant (TRAIN-03's canonical `base-benchmark-v1.jsonl` was briefly edited in place, caught by its own drift test, and reverted before any commit — see `reports/vertex/gemini-2.5-flash-v1-promptv3/error-analysis.md` for the full account). `scripts/run_vertex_benchmark.py` gained `--base-benchmark-dataset`/`--output-dir` flags so a future prompt-version re-run doesn't require another near-duplicate script. Total actual spend for Group 7 across both attempts: ~$1.65, not the ~$0.84 originally estimated.
4. **Java SDK coordinate resolved via `javap` against the real downloaded jar** (`com.google.genai:google-genai:1.68.0`), not documentation — a web-search summary incorrectly suggested `.enterprise(true)` was the Vertex AI mode flag; the real flag, confirmed in bytecode, is `.vertexAI(true)`.

## Validation Commands (as actually run this session)

- `cd backend && ./mvnw -o test` — **495/495 passed** (default suite, integration-tagged tests excluded).
- `cd backend && ./mvnw -o test -Pintegration-test -Dtest=ThesisRateLimiterIT` — **3/3 passed** (real Redis via Testcontainers, Docker available this session).
- `cd vis-model-training && .venv/bin/pytest` — **134/134 passed** throughout, including after the dataset regeneration and the base-benchmark-v1.jsonl revert.
- `GOOGLE_CLOUD_PROJECT=vis-version0 PYTHONPATH=src .venv/bin/python3 scripts/run_vertex_benchmark.py --output-dir vertex-gemini-2.5-flash-v3 --base-benchmark-dataset datasets/benchmark/base-benchmark-v1-promptv3.jsonl` — the corrected Group 7 live corpus re-run, 574/574, 0 errors.
- Metrics/semantic-validator commands: same pattern as TA3's own validation.md, against `results/vertex-gemini-2.5-flash-v3/`.
- `git diff --stat main` — confirmed to match this phase's declared file list (see the commit for the exact set).

## Manual Review

- [x] Read `ThesisGenerationService`'s fallback branch end to end — confirmed a caller never sees raw/malformed model output.
- [x] Confirmed no update/delete endpoint exists on `investment_thesis_result` anywhere in `ThesisController`/`ThesisAdminController`.
- [x] Confirmed no committed file contains a real GCP project id, service-account key, or credential value (`.env.example` values are all blank placeholders).
- [x] Spot-checked `reports/vertex/gemini-2.5-flash-v1-promptv3/error-analysis.md`: the fix's core claim (63/68-equivalent → 63/63 at full scale) is confirmed, not merely projected. The `humanReviewAccuracy` *metric* moved in the opposite direction on two of three datasets — investigated and explained (the project's own template wasn't updated to expect the new behavior; base-benchmark-v1's independent TRAIN-03 gold answers, the more trustworthy of the three comparisons, improved 0.66→0.96) rather than assumed away.

## Merge Readiness

- All acceptance checks satisfied except the final roadmap marking (deliberately last).
- `cd backend && ./mvnw -o test` and `cd vis-model-training && .venv/bin/pytest` both pass with zero regressions; the Docker-gated `ThesisRateLimiterIT` also passed this session.
- `THESIS_AGENT_ENABLED=false` everywhere in the committed diff.

## Known Risks (carried forward, not resolved by this phase)

- **`riskQuality`'s generic-keyRisks-filler pattern** (TA3's finding) is unaffected by this phase's prompt change — still a candidate for a future, separate prompt clarification.
- **The broader "moderate decline / thin margin" pattern** the 19-case experiment and this full-scale confirmation both deliberately left unresolved (e.g. `VIS-BENCH-0026`) remains a possible future prompt-tuning iteration, not addressed here.
- **Production service-account/Secret Manager wiring** for Vertex AI credentials is explicitly out of scope (K-series infrastructure work) — local/dev uses ADC only, this session's decision.
- **The Redis rate-limiter's TTL-counter approach** has a known non-atomic race at the exact limit boundary under concurrent requests — accepted tradeoff at the low default limit, unchanged from the original plan.
