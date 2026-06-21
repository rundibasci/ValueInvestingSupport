# Validation — Group G2: Alert Delivery

## Merge checklist

### Delivery and configuration

- Spring Mail is configured exclusively through external, documented environment variables; no SMTP hostname, username, password, or sender credential is committed.
- A newly persisted, eligible `HIGH` priority alert addressed to a valid owning user produces exactly one email through the fake/containerized SMTP test transport.
- The email contains alert type, symbol, factual trigger context, relevant observed value/threshold, trigger time, application route hint, and MiFID II decision-support disclaimer.
- Lower-priority alerts are not emailed and remain available in-app.
- Missing/invalid recipients and SMTP failures preserve the persisted alert, record a safe observable outcome, and do not block unrelated deliveries or alert detection.
- Retry behavior is bounded and repeat processing/restarts do not send duplicates for an alert already marked sent.

### In-app access and acknowledgement

- `GET /api/v1/watchlist/alerts` returns only the authenticated user's alerts.
- `PUT /api/v1/alerts/{id}/ack` acknowledges the authenticated owner's alert and records the expected lifecycle state/timestamp.
- No-auth requests return 401; unknown or another user's alert cannot be acknowledged and returns the project-standard non-disclosing response (normally 404).
- Repeating acknowledgement has documented, tested behavior and never triggers another email.

### Automated commands

```bash
mvn test -pl backend -Dtest="*Alert*Test,*Alert*IT,*Mail*Test,*Mail*IT"
mvn test -pl backend
mvn flyway:migrate -pl backend
```

All commands must finish successfully. Tests must use fake/containerized SMTP and must not depend on a live SMTP provider, recipient mailbox, FMP/Yahoo credentials, or committed secrets.

### Deferred live-email test (intentional)

Live email delivery is **not tested in G2**. Before production release, a later advanced development phase must validate a configured SMTP provider and approved test mailbox for authentication, receipt, spam/rendering behavior, route links, and failure/retry handling. This deferral is intentional and must remain visible in the merge handoff.

## Final acceptance

The branch is ready to merge when email-only delivery is configurable, high-priority delivery is idempotent and failure-isolated under automated fake-SMTP tests, alert ownership and acknowledgement are secure, and no credentials are present. Live provider acceptance is explicitly deferred as stated above.
