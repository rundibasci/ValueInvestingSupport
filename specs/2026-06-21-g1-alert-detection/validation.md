# Validation — Group G1: Alert Detection Job

## Merge checklist

### Rule coverage

- Each of the eight types is tested with a qualifying persisted-data scenario and creates one `ACTIVE` alert for the owning user.
- `PRICE_TARGET_HIT` triggers at exactly 5% movement in either direction and does not trigger at 4.99%.
- MoS entry/exit use only configured watchlist thresholds.
- Fundamental degradation is skipped when its per-item threshold is null and describes the observed metric when it triggers.
- Dividend, insider, earnings, and rebalance rules use persisted domain data and are explainable from it.

### Deduplication and resilience

- Running the job twice on the same day with unchanged qualifying data produces one row per user/symbol/type, not two.
- Distinct users with the same symbol receive separate alerts.
- A watchlist/portfolio overlap does not duplicate the same user's alert.
- Missing quotes, valuations, snapshots, dividends, insider records, or rebalance inputs skip the affected rule without failing the complete job.
- An individual evaluation failure is logged and does not block unrelated evaluations.

### Automated commands

```bash
mvn test -pl backend -Dtest="*Alert*Test"
mvn test -pl backend -Dtest="*Alert*IT"
mvn test -pl backend
mvn flyway:migrate -pl backend
```

All commands must finish successfully. No test may be disabled or depend on live FMP/Yahoo credentials.

### Manual smoke test

1. Start PostgreSQL/Redis and the backend with the local profile.
2. Seed one user-owned watchlist symbol with current/previous quotes that differ by 5% or more, plus an eligible valuation.
3. Invoke the alert job directly through its supported local trigger or wait for the configured schedule.
4. Query `GET /api/v1/watchlist/alerts` as that user and confirm a factual `PRICE_TARGET_HIT` alert appears once.
5. Invoke the job again on the same day and confirm the alert count does not increase.
6. Repeat with a second user's same symbol and confirm that user's alert is separate and inaccessible to the first user.

### Final acceptance

The branch is ready to merge when all eight rule categories, idempotency, ownership isolation, failure isolation, migration checks, and the full backend test suite pass. G2 remains responsible for delivering, acknowledging, or dismissing these persisted alerts.
