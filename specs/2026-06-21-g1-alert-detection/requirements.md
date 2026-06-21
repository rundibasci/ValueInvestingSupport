# Requirements — Group G1: Alert Detection Job

## Scope

Implement the first part of M7: a scheduled backend job that detects and persists all eight existing alert types for symbols owned by users through watchlists and portfolios. It runs nightly after the quote-refresh work and never sends notifications; delivery and acknowledgement remain G2 responsibilities.

The job must recompute margin of safety (MoS), evaluate the latest persisted market and fundamental data, and create user-scoped `Alert` records only when a condition is newly met for that user and symbol on that calendar day.

## Context

- The roadmap requires a nightly job, all eight alert types, persisted alerts, and same-day deduplication.
- Existing `Alert` infrastructure already provides `Alert`, `AlertType`, `AlertStatus`, and the active-alert read endpoint.
- The platform is decision support, not investment advice. Alert text and data must be factual, explainable, and must not make buy/sell recommendations.
- Data remains cache/database first. The job consumes the current persisted state and existing valuation/data services; it must not introduce an uncached bulk live-data dependency.

## Alert rules

| Alert type | Evaluation rule | Threshold/source |
|---|---|---|
| `MOS_ENTRY` | Latest MoS is at or above the watchlist item's configured entry threshold. | `mosAlertMin` |
| `MOS_EXIT` | Latest MoS is at or below the watchlist item's configured exit threshold. | `mosAlertMax` |
| `PRICE_TARGET_HIT` | Latest close differs from the immediately preceding stored close by at least 5% in either direction. | Fixed 5% movement, requested by product owner |
| `FUNDAMENTAL_DEGRADE` | Latest available fundamental-quality measure is below the user's configured degradation threshold. | `fundamentalDegradeThreshold`; no alert when absent |
| `DIVIDEND_CUT` | Latest dividend per share is lower than the preceding comparable dividend payment. | Persisted dividend history |
| `INSIDER_SELL` | A newly ingested insider transaction is a sale. | Persisted insider trades |
| `EARNINGS_SURPRISE` | Latest reported EPS is below the immediately previous comparable reported EPS. | Persisted fundamental snapshots |
| `REBALANCE_NEEDED` | Existing portfolio rebalancing rules produce at least one actionable rebalance line. | Portfolio/rebalance service |

The values above are deliberately transparent. The product owner specified the 5% price-movement threshold. For fundamental deterioration, the existing per-watchlist threshold controls the condition; its score/metric definition must be exposed in the alert context rather than implied as a universal quality judgement.

## Decisions

| Decision | Value |
|---|---|
| Schedule | Nightly, after quote refresh; cron and timezone are external configuration. |
| Ownership | Produce an alert for each affected user, never a shared/global alert. A symbol in both a user's watchlist and portfolio still creates at most one alert per type/day. |
| Persistence | New alerts use `ACTIVE` status. G1 does not acknowledge, dismiss, email, or alter pre-existing alert lifecycle data. |
| Deduplication | Unique logical key: user + symbol + alert type + local trigger date. Re-runs and repeated qualifying data cannot create a second record the same day. |
| Missing data | Skip only the rule that lacks sufficient data; log/measure the skip and continue processing the remaining rules and symbols. |
| Traceability | Persist the existing threshold field and add sufficient structured context (observed value/direction and evaluation date) for later delivery/UI work. If the current schema cannot retain that context, introduce a forward-compatible Flyway migration. |
| Transactionality | Isolate failures at symbol/user evaluation level so one corrupt record or unavailable calculation does not abort the nightly run. |

## Out of scope

- Email, SMTP/SendGrid configuration, and any notification delivery.
- Alert acknowledgement and dismissal endpoints.
- New React or HTML UI work.
- Live external market-data calls solely for alert detection.
- Changing portfolio allocation or watchlist thresholds as a side effect of an alert.
