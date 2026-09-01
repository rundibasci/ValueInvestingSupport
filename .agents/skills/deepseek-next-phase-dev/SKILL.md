---
name: deepseek-next-phase-dev
description: Spec-driven development workflow for the Value Investing Advisory Platform. Use when starting a new feature phase, creating specs, writing plans/requirements/validation docs, or when the user mentions DPSK, spec-driven development, feature specs, "next phase," or the DeepSeek engine. Reads specs/roadmap.md, specs/mission.md, and specs/tech-stack.md for project conventions.
---

# DeepSeek Next Phase Dev — Spec-Driven Development

This Skill drives the spec-first workflow for the Value Investing Advisory Platform. Every new feature phase begins with a spec — never with code.

## Quick start

1. Read `specs/roadmap.md` to identify the next incomplete phase.
2. Ask the user clarifying questions grouped on three areas (see below).
3. Create a branch named `feature/<phase-name>`.
4. Create `specs/YYYY-MM-DD-<phase-name>/` with `plan.md`, `requirements.md`, and `validation.md`.
5. Do not write implementation code until the user explicitly approves the spec and asks to proceed.

## Instructions

### Step 1: Determine the next phase

Read `specs/roadmap.md` and identify the next phase that is not marked `(complete)`. If the user has already named a phase, verify it exists in the roadmap. Read `specs/mission.md` and `specs/tech-stack.md` for design principles, stack constraints, and secrets/config conventions.

Present the phase name, its goal from the roadmap, and confirm with the user before proceeding.

### Step 2: Ask clarifying questions (grouped on 3 areas)

Use the `AskUserQuestion` tool once with **exactly 3 questions** — one per area. Group them; do not ask one at a time.

**Question 1 — Scope & Requirements:**
- What is the specific problem this phase solves?
- What is explicitly in scope? What is explicitly out?
- What dependencies does it have on previous phases?
- Are there any decisions already made (e.g., provider, library, architecture)?
- What user roles are affected?

**Question 2 — Plan & Task Breakdown:**
- What are the major task groups (numbered, 3–10 groups)?
- What is the natural implementation order?
- Are there any parallelizable workstreams?
- What config, migrations, or infrastructure scaffolding must come first?

**Question 3 — Validation & Success Criteria:**
- How will we know the phase is done and merge-ready?
- What specific test scenarios must pass?
- What manual verification steps are needed?
- What regression checks must not break?
- What's the merge gate?

### Step 3: Create the branch

```bash
git checkout -b feature/<phase-name>
```

Use the phase name from the roadmap (e.g., `feature/k1-stakeholder-cloud-deployment`).

### Step 4: Create the spec directory and files

```bash
mkdir -p "specs/YYYY-MM-DD-<phase-name>"
```

The date must be today's date in ISO format.

#### `requirements.md` — Scope, Decisions, Context

Structure:
```markdown
# <Phase> — <Title>

## Context
<!-- Why this phase exists, what problem it solves, what it depends on -->

## Scope
<!-- What is in scope, organized by concern (submission behavior, data model, API, frontend, etc.) -->

## Decisions
<!-- Numbered list of architecture/design decisions with rationale -->

## Out of Scope
<!-- Explicitly excluded items -->

## Compatibility and Risks
<!-- Backward compatibility, concurrency, security, data risks -->
```

Refer to `specs/mission.md` for design principles and `specs/tech-stack.md` for stack constraints.

**Key conventions:**
- Every API endpoint is named with its HTTP method and path.
- Environment variables are in `UPPER_SNAKE_CASE`, Spring properties in `kebab-case`.
- Secrets never appear in committed files.
- The MiFID II decision-support disclaimer applies to all valuation/score/recommendation outputs.

#### `plan.md` — Numbered Task Groups

Structure:
```markdown
# <Phase> — Implementation Plan

## 1. <Task Group Name>
1. <Specific task>
2. <Specific task>

## 2. <Task Group Name>
1. <Specific task>
...
```

Task groups are ordered by natural implementation sequence:
1. Contracts, configuration, and migrations first
2. Core business logic
3. API / endpoints
4. Authorization and safety
5. Frontend
6. Testing and merge readiness

#### `validation.md` — Success Criteria & Merge Gate

Structure:
```markdown
# <Phase> — Validation

## Acceptance Criteria
- [ ] Criterion

## <Backend/Frontend> Test Matrix
| Scenario | Expected result |
|---|---|

## Regression Checks
- [ ] Check

## Verification Commands
\```bash
# Backend
cd backend && ./mvnw test
# Frontend
cd frontend && npm test -- --run
cd frontend && npm run typecheck
cd frontend && npm run build
git diff --check
\```

## Manual Validation
1. Step

## Merge Gate
<!-- Conditions that must be true before merge -->
```

### Step 5: Present the spec and wait for approval

After writing the three files, present a concise summary and ask the user to review. Do not write any implementation code until the user explicitly confirms the spec and asks to proceed with implementation. Writing the spec files is not itself approval to implement.

### Step 6: Implement, validate, and merge

This runs in a later turn, once the user has approved the spec and asked to proceed — do not skip it even when the go-ahead is a short "proceed"/"implement it":

1. Implement strictly against `plan.md`'s numbered task groups, on the `feature/<phase-name>` branch created in Step 3.
2. Validate against `validation.md`'s acceptance criteria — run every command in its Verification Commands section and confirm the Merge Gate conditions are actually true, not assumed.
3. **Merge directly — do not open a pull request.** This project has a single GitHub account that is both the only login and every PR's author, so GitHub blocks self-approval and any PR would sit unapproved indefinitely (confirmed 2026-09-01 on PR #9). Once the merge gate passes: `git checkout main && git merge --no-ff feature/<phase-name> && git push origin main`, then delete the branch (local and remote).
4. Update `specs/roadmap.md` to mark the phase `*(complete)*` as part of the same merge, not a follow-up.

## Best Practices

- **Use the roadmap as source of truth.** The `specs/roadmap.md` defines phases, their order, and their goals. If the user's request conflicts with the roadmap, say so.
- **Group `AskUserQuestion` calls.** Always present all three questions (scope, plan, validation) in one call. Never ask one at a time.
- **Write specs for the platform, not for yourself.** Follow the conventions visible in completed specs like `specs/2026-07-15-dl5-asynchronous-bulk-seed-progress/`.
- **Secrets and config follow tech-stack.md.** API keys go in `.env` (local) or Secret Manager (cloud). Never in committed files. Spring properties use `kebab-case` with env-var fallbacks: `${property.name:${ENV_VAR:}}`.
- **Respect the decision-support boundary.** Fair value, MoS, scores, and recommendations carry the MiFID II disclaimer. The platform is a research tool, not a regulated advisor.
- **Branch naming:** `feature/<phase-name>` matching the roadmap phase identifier (e.g., `feature/k1-stakeholder-cloud-deployment`) — the one fixed convention across the project; do not use `phase/`, `fix/`, or an unprefixed branch name.
- **Spec directory naming:** `specs/YYYY-MM-DD-<phase-name>/` with today's date.
- **No pull requests in this repo.** The only GitHub account is also every PR's author, so self-approval is blocked and a PR would stall forever. Merge phase branches directly into `main` once `validation.md`'s merge gate passes.
