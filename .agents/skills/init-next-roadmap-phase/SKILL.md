---
name: init-next-roadmap-phase
description: Find the next phase on specs/roadmap.md, create a new branch containing GEMINI, ask about the feature spec, and initialize a specs subfolder with plan.md, requirements.md, and validation.md after prompting the user with the ask_question tool.
---

# Initialize Next Roadmap Phase

This skill automates finding the next pending feature/phase from `specs/roadmap.md`, creating a new git branch, and initializing the necessary specification files after user confirmation.

## Instructions

When this skill is triggered:

1. **Find the Next Phase**:
   - Read [roadmap.md](file:///specs/roadmap.md) to locate the next planned phase/feature that has not yet been started or implemented.

2. **Create Git Branch**:
   - Create a new git branch for this feature.
   - The branch name **must** contain `gemini` (case-insensitive, e.g., `gemini/feature-name` or `feature-name-GEMINI`).

3. **Ask the User about the Feature Spec**:
   - Discuss the feature spec with the user to gather context, requirements, scope, and decisions.

4. **Prepare Draft Specification Files**:
   - Refer to [mission.md](file:///specs/mission.md) and [tech-stack.md](file:///specs/tech-stack.md) for guidance on architecture, tech stack, and goals.
   - Draft the following files:
     - `requirements.md`: Scope, decisions, and context.
     - `plan.md`: A series of numbered task groups.
     - `validation.md`: Clear criteria on how to know the implementation succeeded and can be merged.

5. **User Confirmation (Mandatory)**:
   - **CRITICAL**: Before writing any files to disk, you **must** use the `default_api:ask_question` tool.
   - Group the confirmation/questions for all three files (`plan.md`, `requirements.md`, `validation.md`) into a single `ask_question` call to get the user's feedback or approval.

6. **Create Feature Directory and Files**:
   - Once approved by the user, create a new directory under `specs/` named `YYYY-MM-DD-feature-name` (using the current date and feature name).
   - Write the approved `requirements.md`, `plan.md`, and `validation.md` into this folder.
