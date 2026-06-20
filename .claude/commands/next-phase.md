Read specs/roadmap.md and identify the next unimplemented phase (the first group/phase not yet present as a directory under specs/).

Then read the two most recent spec directories (newest by date prefix) — specifically their plan.md, requirements.md, and validation.md — to calibrate the depth, format, and level of detail expected.

Also read specs/mission.md and specs/tech-stack.md for architectural guidance.

Create the git branch for this phase (e.g. phase/group-e-security-detail).

Then use your AskUserQuestion tool with ALL FOUR questions below in a single call — do not write any files until you have the answers:

1. **Phase & Scope** — Confirm which phase is next and what is in/out of scope. Are there any phases to skip or reorder? Any features to cut or add vs the roadmap description?

2. **Implementation decisions** — What are the key technical choices for this phase? (e.g. which existing services to reuse, preferred patterns, config approach, package layout, any constraints from prior phases that affect this one)

3. **Validation & definition of done** — What tests are required (unit / integration / manual curl)? Any performance targets, data volume assertions, or merge criteria beyond the standard suite?

4. **Naming & specifics** — Preferred branch name, spec directory name (YYYY-MM-DD-slug), and any class/endpoint/field names that differ from what the roadmap implies.

After receiving all four answers, write the three spec files:
- specs/YYYY-MM-DD-feature-name/plan.md — numbered task groups (1.1, 1.2 … 2.1 … etc.), each task a concrete implementation step with class names, method signatures, and test cases, matching the depth of the two reference iterations you read
- specs/YYYY-MM-DD-feature-name/requirements.md — scope table, context (what exists / what's introduced), decisions with rationale, request/response shapes, out-of-scope list
- specs/YYYY-MM-DD-feature-name/validation.md — exact mvn test commands, integration test assertions table, manual curl sequence, merge criteria checklist

Do not ask any further questions after writing these files.

**Important — scope per branch:** Each roadmap phase (e.g. E1, E2, E3) must be treated as a separate branch and separate spec directory unless the user explicitly asks to combine phases. Do not merge multiple phases into one branch on your own initiative.
