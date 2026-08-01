# TRAIN-00 — Implementation Plan

## 1. Reconcile Phase State and Evidence

1. Inventory the current `vis-model-training` files and distinguish implemented exploratory work from completed TRAIN phases.
2. Record the official source URLs, document versions, and access dates used for model and licence decisions.
3. Define the evidence required for an explicit TRAIN-00 GO/NO-GO decision.

## 2. Record Student and Teacher Decisions

1. Create `vis-model-training/docs/adr/ADR-001-model-selection.md` covering the 4B student, 27B teacher, rejected alternatives, revision pinning, and fallback criteria.
2. Explain why teacher selection does not authorize unreviewed teacher output to enter a dataset release.
3. Link the decision to later TRAIN-03, TRAIN-05, TRAIN-07, and TRAIN-09 gates.

## 3. Formalize Licences and Data Governance

1. Create `vis-model-training/docs/governance/data-and-model-licenses.md` with Gemma terms, prohibited-use policy, model cards, derivative/distribution obligations, dataset provenance, and legal-review boundary.
2. Create `vis-model-training/docs/governance/data-policy.md` with permitted/prohibited data, provenance requirements, review states, retention expectations, and escalation rules.
3. Add the notice and licence artefact requirements that later packaging must satisfy without copying secrets or gated model files into Git.

## 4. Document the Hardware Boundary

1. Create `vis-model-training/docs/hardware/local-environment.md` from observed host facts, explicitly marking unavailable values.
2. Define local-host permitted activities and the external NVIDIA GPU reference profile.
3. Separate minimum planning assumptions from TRAIN-07 measurements so VRAM capability is not overstated.

## 5. Verify Repository Hygiene

1. Inspect tracked `vis-model-training` content and relevant ignore rules for secrets, model artefacts, generated outputs, and local caches.
2. Run available secret-detection checks and a targeted pattern scan, recording only sanitized counts and commands.
3. Add narrowly scoped ignore rules or documentation where gaps are found.

## 6. Align the TRAIN Roadmap

1. Update `vis-model-training/README.md` with the delivered TRAIN-00 status and decision.
2. Mark existing TRAIN-01 and TRAIN-02 work as exploratory until their own acceptance criteria pass.
3. Correct stale paths or statements discovered while reconciling the implemented tree.

## 7. Validate and Prepare for Review

1. Run the existing Node dataset validator to ensure documentation changes do not disturb TRAIN-01 contracts.
2. Check documentation links, file paths, Markdown formatting, tracked-file hygiene, and `git diff --check`.
3. Review all evidence against TRAIN-00 acceptance criteria and record the final conditional GO/NO-GO outcome.
