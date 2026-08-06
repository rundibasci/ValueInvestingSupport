---
name: obsidian-project-notes
description: Record ValueInvestingSupport project progress, phase handoffs, experiment results, implementation decisions, costs, failures, and next-session context as Obsidian Markdown notes. Use when the user asks to write, update, register, archive, or summarize project work in Obsidian, especially for TRAIN or roadmap feature phases.
---

# Obsidian Project Notes

Write concise, evidence-backed project notes to the primary vault:

`/Users/marcello.mazzoni/Documents/valueinvestorsupport/ValueInvestingSupport`

Use `/Users/marcello.mazzoni/Documents/valueinvestorsupport/VIS-Model-Training` only when the user explicitly requests the dedicated model-training vault.

## Workflow

1. Resolve the phase from the request, active branch, approved spec, or roadmap. Do not invent a phase identifier.
2. Inspect authoritative repository evidence before writing: relevant specs/reports, `git status`, current branch, and recent commits. For experiments, distinguish measured values from estimates.
3. Search the target vault for an existing note with the same date and phase prefix. Update that note when it covers the same work; otherwise create one.
4. Name every new note with this mandatory prefix:

   `yyyy-mm-dd-featurePhase`

   Preferred full form: `yyyy-mm-dd-featurePhase - Concise title.md`.

   Example: `2026-08-06-TRAIN-05 - Teacher smoke and bulk handoff.md`.
5. Use YAML frontmatter with `title`, `date`, `status`, `tags`, and `aliases` when useful.
6. Include only sections supported by the work: objective, completed work, environment, results, failures/lessons, costs, artifact/checksum references, repository references, open gates, and exact next step.
7. Preserve safety boundaries: never copy tokens, secrets, raw provider payloads, payment details, or sensitive local configuration. Record secret presence/configuration only as a boolean fact.
8. Keep canonical and recovered/derived metrics separate. Never present a recovery as canonical success.
9. Link repository artifacts with readable paths and record branch plus commit SHA. State whether changes were pushed, merged, or still isolated.
10. After writing, reopen the note, verify the filename prefix and essential facts, and report the absolute note path.

## Handoff standard

For a phase handoff, always state:

- current phase status and what remains incomplete;
- last pushed branch and commit;
- resources still running or confirmation that paid resources were removed;
- measured provider cost and projection assumptions when applicable;
- authorization boundary for the next action;
- the first command or decision needed in the next session.

Do not mark a phase complete merely because one experiment or smoke test finished.
