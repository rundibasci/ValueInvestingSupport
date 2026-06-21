# Plan — Group G2: Alert Delivery

## Task Group 1: Establish alert delivery and lifecycle contracts

1.1 Inspect the existing `Alert`, `User`, alert-priority/status types, repositories, watchlist alert endpoint, and security conventions from G1/F1.

1.2 Define the delivery-state model needed to distinguish pending, sent, failed, skipped, and retryable alerts without changing the factual detection record.

1.3 Add a Flyway migration only when existing storage cannot safely record delivery attempts, sent timestamps, failure diagnostics, or acknowledgement timestamps.

1.4 Document configurable SMTP and sender variables in `.env.example`, with placeholders only and no credentials.

## Task Group 2: Build the email delivery service

2.1 Configure Spring Mail through environment-backed SMTP properties and provide a safe disabled/no-op behavior when mail is not configured in local/test environments.

2.2 Implement a focused alert-email composer that creates factual, accessible text/HTML from the persisted alert context and includes the MiFID II disclaimer.

2.3 Implement delivery eligibility: only `HIGH` priority, user-owned, unsent active alerts with a valid recipient address proceed to Spring Mail.

2.4 Persist delivery outcomes atomically enough to prevent duplicate emails across retries, restarts, and concurrent delivery workers; record safe failure reasons without exposing credentials.

2.5 Apply bounded, configurable retry/backoff handling. A single invalid recipient or SMTP failure must not block other alerts or G1 detection.

## Task Group 3: Expose in-app alert lifecycle APIs

3.1 Confirm or complete `GET /api/v1/watchlist/alerts` so it returns the authenticated user's active alerts, including delivery-relevant factual status where appropriate, without leaking another user's data.

3.2 Implement `PUT /api/v1/alerts/{id}/ack` for the owning authenticated user; return the updated alert representation or the project-standard success response.

3.3 Define and test error behavior: unauthenticated request (401), another user's or unknown alert (404), and repeat acknowledgement (idempotent success or documented conflict, selected consistently with existing API conventions).

## Task Group 4: Test without live email delivery

4.1 Add unit tests for eligibility, recipient validation, message composition, disclaimer presence, state transitions, retry limits, and failure isolation.

4.2 Add controller/security tests for listing and acknowledging alerts, including strict ownership isolation.

4.3 Add integration tests using a fake or containerized SMTP server to verify an eligible high-priority alert produces one message and records a sent state; verify lower-priority, duplicate, invalid-recipient, and failed-send cases do not create duplicate delivery.

4.4 Run Flyway and the backend test suite without requiring live FMP/Yahoo or SMTP credentials.

## Task Group 5: Deferred advanced-phase live email verification

5.1 **Do not perform real SMTP/provider or mailbox delivery tests during G2 development.** Mark this work as intentionally untested in the implementation handoff.

5.2 In a later, advanced pre-production phase, validate real environment configuration, provider authentication, recipient delivery, spam/rendering behavior, link routing, and retry behavior with an approved test mailbox.

## Task Group 6: Review and merge readiness

6.1 Run targeted alert and mail tests, then `mvn test -pl backend`.

6.2 Verify configuration documentation contains no secrets and all mail transport settings are externally configurable.

6.3 Confirm no user can read, acknowledge, or trigger delivery for another user's alerts.

6.4 Record automated/fake-SMTP evidence in `validation.md`; explicitly record that live provider delivery remains deferred.
