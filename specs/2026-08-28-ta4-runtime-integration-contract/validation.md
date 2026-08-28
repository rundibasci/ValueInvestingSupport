# TA4 — Validation

## Acceptance Checks

- [ ] `V27__investment_thesis_result.sql` applies cleanly on a fresh DB and on top of `V26`; the table has no update-in-place path in the JPA repository (structural check, not just runtime).
- [ ] `InvestmentThesisClient`/`VertexAiInvestmentThesisClient` exist; every test mocks the SDK client — `grep -rn "GOOGLE_APPLICATION_CREDENTIALS\|vertexai\." backend/src/test/` (excluding the gitignored `application-vertexkey.yml`-gated tests) returns nothing.
- [ ] `ThesisConfigParityTest` passes: the Java-side decoding config (`temperature`, `thinkingBudget`, `maxOutputTokens`, `responseSchema`) matches the checked-in fixture derived from `vis-model-training/config/vertex-gemini-v1.json`.
- [ ] All three endpoints (`generate`, `status`, `latest`) and the admin review-queue endpoint exist, are wired to Spring Security consistent with existing role checks, and return the exact response shapes in `plan.md` → Group 5.
- [ ] Deterministic fallback: a simulated `TIMEOUT`/`SCHEMA_VALIDATION_FAILED` (after retries exhausted) persists `status=FAILED` with `classification=UNDER_REVIEW`, `humanReviewRequired=true`, empty bull/bear case, tracked error reason — never a second-model fallback, never partial/malformed text returned to a caller.
- [ ] Rate limiting: the `THESIS_GENERATION_DAILY_LIMIT`+1th request in a UTC day is rejected with a structured `RATE_LIMIT_EXCEEDED` body, not a generic `500`; applies identically to every authenticated role (no ADMIN bypass).
- [ ] Review queue lists `status=HUMAN_REVIEW_PENDING` **or** non-empty `dataWarnings` rows, ADMIN-only.
- [ ] `THESIS_AGENT_ENABLED` defaults to `false` in every committed config; not flipped to `true` anywhere by this phase.
- [ ] `prompts/system-prompt-v3.txt` exists (v2 unmodified, byte-identical to `main`); `config/vertex-gemini-v1.json` points `promptPath`/`promptVersion` at v3.
- [ ] **The full 574-case corpus has been re-run against v3** (`results/vertex-gemini-2.5-flash-v3/`), metrics recomputed, TRAIN-02 semantic validator run against the new real output with zero genuine violations, and the 6 previously-verified cases confirmed `humanReviewRequired=true` in the full run with zero new false positives — `reports/vertex/gemini-2.5-flash-v1-promptv3/` written.
- [ ] `.env.example` updated with the 7 new variables; `.gitignore` rule added for the local service-account key path; `application-vertexkey.yml` pattern documented and gitignored.
- [ ] `specs/roadmap.md` → Phase TA4 marked `*(complete)*`.

## Validation Commands

- `cd backend && mvn test` — full backend suite, zero regressions.
- `cd backend && mvn -Dtest=ThesisConfigParityTest,VertexAiInvestmentThesisClientTest,ThesisGenerationServiceTest,ThesisControllerTest,ThesisAdminControllerTest,ThesisRateLimiterTest test` — narrowest TA4-specific run, if the full suite is slow/blocked locally (document the limitation if used instead of the full run, per this project's existing backend-validation convention).
- `cd vis-model-training && .venv/bin/pytest` — zero regressions from the Group 7 prompt/config changes.
- `PYTHONPATH=src .venv/bin/python3 scripts/run_vertex_benchmark.py` (pointed at `system-prompt-v3` via the updated config) — the Group 7 live corpus re-run; **real spend (~$0.84), requires explicit authorization before running**, same discipline as TA3's own live run.
- `PYTHONPATH=src .venv/bin/python3 -m vis_training.benchmark.cli metrics ...` (×3 datasets) — recompute metrics against the v3 results.
- `diff <(python3 -c "import json; print(json.dumps(json.load(open('vis-model-training/config/vertex-gemini-v1.json'))['generationConfig'], sort_keys=True))") <(cd backend && mvn -q -Dtest=ThesisConfigParityTest#dumpEffectiveConfig test)` (or equivalent) — confirms no drift between the two configs, exact mechanism to be finalized during implementation.
- `git diff --stat main` — confirms the diff is limited to: this spec's 3 files, `backend/src/main/java/it/mazzoni/vis/thesis/**`, `backend/src/main/resources/db/migration/V27__investment_thesis_result.sql`, `backend/src/test/**` (thesis-related), `backend/.env.example`, `.gitignore`, `vis-model-training/prompts/system-prompt-v3.txt`, `vis-model-training/config/vertex-gemini-v1.json`, `vis-model-training/results/vertex-gemini-2.5-flash-v3/`, `vis-model-training/reports/vertex/gemini-2.5-flash-v1-promptv3/`, `specs/roadmap.md`. No `frontend/`, `terraform/`, or K-series infrastructure change; no in-place edit to `prompts/system-prompt-v2.txt`, `thesis-input.schema.json`, or `thesis-output.schema.json`.

## Manual Review

- Read `ThesisGenerationService`'s fallback branch end to end and confirm a caller can never see raw/malformed model output — only the schema-conforming synthetic fallback or a genuinely valid `READY`/`HUMAN_REVIEW_PENDING` thesis.
- Confirm `investment_thesis_result` has no exposed update/delete endpoint anywhere in `ThesisController`/`ThesisAdminController` (immutability check, mirroring PW1's audit-entity convention: "confirm audit entities do not expose update/delete operations").
- Confirm no committed file contains a real GCP project id, service-account key, or credential value.
- Spot-check the v3 corpus re-run's `reports/.../error-analysis.md`: does the accept-rate-margin improvement actually materialize close to the 0.897 TA3 projected, or does the full run reveal something the 19-case experiment didn't catch? Record the real figure, don't assume the projection.

## Merge Readiness

- All acceptance checks above are satisfied, including the Group 7 corpus re-run — this is not deferrable the way TA3's live run was once deferred from TA1/TA2; TA4 is the phase that must actually close it.
- `cd backend && mvn test` and `cd vis-model-training && .venv/bin/pytest` both pass with zero regressions.
- Implementation and tests are committed on `phase/ta4-runtime-integration-contract`.
- `THESIS_AGENT_ENABLED=false` everywhere in the committed diff.

## Known Risks

- The exact Java Vertex AI SDK coordinate is unconfirmed as of this spec's writing (see `requirements.md` → Compatibility and Risks) — Group 3, step 1 must resolve this before the client can be implemented; if the Java SDK's request-construction surface differs materially from the Python `google-genai` precedent (e.g. different `responseSchema`/`thinkingConfig` field names), the same kind of live-call-discovered defect TA3 hit twice in Python could recur in Java and would need its own fix-and-reverify cycle, not be assumed away by this plan.
- The Redis rate-limiter's TTL-counter approach has a known non-atomic race at the exact limit boundary under concurrent requests (documented, accepted tradeoff at `THESIS_GENERATION_DAILY_LIMIT`'s low default value — see `requirements.md`).
- The partial-index predicate for the review-queue query (`plan.md` → Group 1, step 2) may need adjustment once tested against real Postgres 16 JSONB emptiness semantics — flagged as an implementation-time verification step, not assumed correct as written.
- The v3 corpus re-run could, in principle, surface a regression the 19-case experiment's controls didn't cover (500 TRAIN-04 scenarios is a much larger surface) — if so, this phase must not silently ship v3 with `THESIS_AGENT_ENABLED` planned for a future `true` flip; it must document the regression and decide (fix again, revert to v2, or accept with a recorded rationale) before Phase TA4 can be marked complete.
