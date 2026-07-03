# RCL Replay Triage Template

Use this table for every investor-agent plus monitor-agent recycling cycle.

| ID | Severity | Category | Status | Owner | Route/API | Reproduction Path | Investor Observation | Monitor Evidence | Decision | Target Phase |
|---|---|---|---|---|---|---|---|---|---|---|
| RCL2-001 | High/Medium/Low | data-quality gap / UI contradiction / API validation defect / provider limitation / accessibility issue / product follow-up | Open / Fixed / Deferred / Accepted Risk | Team or phase | `/route` or `METHOD /api/path` | Steps to reproduce | What the investor agent saw | Log/status/screenshot references | Fix, defer, or accept | RCL3/RCL4/K1 blocker/etc. |

## Gate Rules

- Any new high- or medium-severity finding fails the recycling gate.
- Failed gates require a fix or an explicit deferral with owner, rationale, and target phase before K1.
- K1 readiness requires two consecutive clean investor-agent plus monitor-agent cycles with no new high/medium findings, no unexplained backend `5xx`, no frontend console errors, no raw authorization failure, and no data-quality contradiction.
- Low-severity findings may pass only when accepted or deferred with rationale.

## Decision-Support Boundary

Replay output can describe model evidence, data availability, and workflow quality. It must not describe a shortlist, watchlist, or demo portfolio as investable, personalized, or recommended for trading.
