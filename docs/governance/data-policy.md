# Data Policy — Third-Party API Data Egress

- Introduced: 2026-08-27, Phase TA1 (`specs/roadmap.md` → Group TA)
- Scope: what VIS-computed data may leave the platform to a third-party managed API, and under what conditions. Vertex AI Gemini is the first API this policy governs; the same pattern applies to any future third-party API integration.
- Legal status: engineering policy, not legal advice. External counsel/authorized business review remains required before any commercial, customer-facing release that sends data to a third-party API — this document does not substitute for that review.

## Why This Document Exists

`specs/mission.md` already states the platform's core design principles (data before opinion, conservative defaults, decision-support boundary, secrets never in source control, immutable historical data). Those principles govern the platform generally. This document exists for one narrower, recurring question those principles don't individually answer: **when VIS calls an external, third-party managed API as part of its own processing (not a market-data provider like FMP/Yahoo, but an interpretation/inference service), what may be sent, and what may never be sent?**

This is distinct from `vis-model-training/docs/governance/data-and-model-licenses.md`, which governs *model/dataset licensing and redistribution terms* for the `vis-model-training/` subproject. This document governs *data egress from the running application* to any third-party API the backend calls at runtime — Vertex AI Gemini today, potentially others later.

## What May Be Sent

VIS-computed, already-derived financial context that the platform itself calculated or ingested and holds as reference data:

- Valuation outputs already computed by VIS's own deterministic engines (DCF, Graham Number, DDM, Margin of Safety, Value Score, Moat Assessment) and their documented input parameters.
- Company/security identifiers and profile fields already stored as platform-wide reference data (symbol, sector, exchange, company description) — not raw, unprocessed provider payloads.
- Structured summaries of financial-health, growth, dividend, and risk indicators already computed and stored by VIS.

All such data must already exist as VIS-computed or VIS-ingested reference data before it is sent — this policy does not authorize a third-party API to compute anything VIS itself is responsible for computing (see `specs/mission.md` Principle 15: an AI integration may only interpret and narrate VIS-computed context, never compute it).

## What Must Never Be Sent

- **Raw user PII** — no user email, name, account identifier, or any other personally identifying data about a platform user.
- **Credentials or secrets of any kind** — API keys, JWT tokens, session identifiers, database credentials, service-account keys. These never appear in a request payload to any third-party API; they only authenticate the request itself, via the mechanism described below.
- **Full, unprocessed provider payloads** — raw FMP/Yahoo API responses are not forwarded wholesale to a third-party API; only the specific derived fields the integration's documented contract requires may be included.
- **Data outside the integration's own documented input schema** — each third-party API integration defines an explicit input contract (e.g. Vertex AI Gemini's `thesis-input.schema.json`, see `vis-model-training/schemas/`); a request may only include fields that schema defines. Sending additional, undocumented fields "because it might help" is not permitted without updating the schema and this policy first.

## Authentication and Secret Handling

Third-party API calls authenticate using the same credential-handling class already established for every other secret in this platform (`specs/mission.md` Principle 7 — secrets never in source control):

- **Local development:** a gitignored local key file or environment variable, never committed, mirroring the existing `.env` / `application-fmpkey.yml` pattern.
- **Deployed environments:** Secret Manager-injected credentials bound to the runtime service account, never a static key embedded in an image or Terraform state.
- No third-party API integration introduces an exception to this handling class. If an integration's authentication mechanism cannot be handled this way, the integration itself must be reconsidered before this policy is amended to accommodate it.

## Vendor Data-Use Commitments

Before any third-party API is integrated, its data-use terms must be reviewed and recorded (see `vis-model-training/docs/governance/data-and-model-licenses.md` for Vertex AI Gemini's specific review) to confirm:

- Whether the vendor uses data sent to the API to train or improve its own models, and under what conditions that could change.
- The region/data-residency options available, and which one this platform selects and why.
- Whether the vendor's terms of service are compatible with sending VIS-derived financial data (not raw user PII) to a managed API, distinct from any terms that would apply to a data-sharing or free-tier product this platform does not use.

This review must be repeated, or its currency reconfirmed, before any material change to which fields are sent, which vendor is used, or which region is selected.

## Regulatory Boundary

- Sending data to a third-party API for interpretation does not change the platform's decision-support classification (`specs/mission.md` Principle 4) — any output derived from that call still requires the MiFID II disclaimer wherever a user sees it.
- A third-party API integration must never be the source of a `BUY`/`SELL`/`HOLD` instruction, regardless of what the vendor's model is capable of producing if asked — the integration's own prompt/schema contract must structurally prevent this (see `specs/mission.md` Principle 15 for the AI Investment Thesis-specific version of this rule).

## Current Integrations Governed by This Policy

| Integration | Status | Governance record |
|---|---|---|
| Vertex AI Gemini (AI Investment Thesis) | Approved as production engine (ADR-002); production integration gated on the TA3 capability benchmark | `vis-model-training/docs/adr/ADR-002-vertex-gemini-selection.md`, `vis-model-training/docs/governance/data-and-model-licenses.md` → Vertex AI Gemini — Governance Review |

Add a row here for any future third-party API integration this platform adds — this table is the single index of what leaves the platform and why, so a later reviewer does not have to search the whole repository to answer that question.
