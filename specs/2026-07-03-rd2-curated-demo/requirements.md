# RD2-1 Requirements - Agent 1 Curated Universe Walkthrough

## Scope

RD2-1 validates the seeds-choice process end to end by having Agent 1, the prudent value investor persona, start from the universe curation workflow instead of manual ticker entry. The phase creates repeatable walkthrough evidence for a curated defensive-quality research universe and documents how the workflow compares with RD1's manual seed flow.

The implementation scope is an evidence pack:

- a deterministic PowerShell replay script for dry-run and live API evidence capture;
- a stakeholder-presentable walkthrough report scaffold;
- a screenshot checklist for the major UI steps;
- spec files that define the execution and validation expectations.

## Roadmap Context

Selected phase: `RD2-1 - Agent 1 Curated Universe Walkthrough & Screenshots`.

This is the first roadmap phase without an existing matching spec directory. It follows Group L conservative workflow hardening and precedes Group K, which is intentionally excluded from this scheduled workflow.

## Functional Requirements

- Start from the `defensive-quality` universe curation template.
- Preview a focused research universe and seed a manageable set of roughly 20 to 30 symbols without manual ticker entry.
- Monitor ingestion progress through job control APIs and per-symbol ingestion evidence.
- Apply the conservative research preset or equivalent manual filters.
- Inspect the top 5 candidates through review, comparison, portfolio, watchlist, dashboard, and alert workflows.
- Capture artifacts that can be used later for screenshots and stakeholder review.
- Preserve the decision-support boundary and avoid buy, sell, or personalised investment advice language.

## Exclusions

- Group K, K1, K2, and K3 cloud deployment work is excluded.
- No production infrastructure, Terraform, Cloud Run, Cloud SQL, Memorystore, or Secret Manager changes.
- No live market-data credentials or secrets are committed.
- The replay pack does not claim an investable model portfolio.
- Browser screenshots are scaffolded for live execution but not generated in dry-run validation.

## Decisions

- Reuse the existing RD1 style: PowerShell replay script plus Markdown report and screenshot checklist.
- Keep the replay script runnable without the backend by supporting `-SkipLiveApi`, which writes a dry-run manifest.
- In live mode, capture API responses as JSON files under the spec evidence folder and redact tokens before writing artifacts.
- Use Yahoo Finance through the existing `realDemo` profile for live validation, consistent with RD1.

## Assumptions

- The `realDemo` stack exposes authentication, admin universe curation, screener, security review, portfolio, watchlist, dashboard, and job-control endpoints on `http://localhost:8080`.
- The exact universe curation API may evolve; live replay uses endpoint paths matching the current frontend/backend naming conventions and writes any failures as evidence instead of hiding them.
- Screenshots are collected manually or by a browser automation pass during live replay, using the checklist in `screenshots/README.md`.
- A dry-run manifest is sufficient repository validation when the real-demo stack is not running.

## Dependencies

- RD1-1 real-demo startup profile.
- RD1-2 full-feature walkthrough conventions.
- L4 conservative research preset, comparison, and workflow enhancements.
- SC1/SC2 universe selection and curation workflow.
