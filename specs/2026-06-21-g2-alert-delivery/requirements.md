# Requirements — Group G2: Alert Delivery

## Scope

Implement the second part of M7: deliver newly created high-priority, user-owned alerts by email and expose the remaining alert lifecycle operations. G1 persists factual `ACTIVE` alerts; G2 makes those alerts visible, acknowledges them, and sends a concise email notification when eligible.

Delivery is **email only**. The implementation uses Spring Mail and a configurable SMTP transport. It must not add SMS, push, webhook, or other delivery channels.

## Context

- The roadmap requires the in-app alert endpoint, email notification for HIGH-priority alerts, and acknowledgement through `PUT /api/v1/alerts/{id}/ack`.
- G1 is merged and provides persisted, user-scoped, deduplicated `Alert` records. Existing `GET /api/v1/watchlist/alerts` infrastructure should be retained or completed rather than duplicated.
- The platform is decision support, not investment advice. Alert text must state the factual trigger, symbol, observed value, threshold where applicable, and the MiFID II decision-support disclaimer. It must not recommend buying or selling.
- Secrets must remain outside version control. SMTP configuration is injected by environment/local `.env`, never source, fixtures, or logs.

## Decisions

| Decision | Value |
|---|---|
| Channel | Email only. |
| Mail integration | Spring Mail (`JavaMailSender`) over configurable SMTP. |
| Eligibility | Send only newly created `HIGH` priority alerts. Non-high-priority alerts remain available in-app. |
| Delivery timing | Attempt delivery as part of the alert-delivery workflow after persistence; failures must not delete, acknowledge, or suppress the underlying alert. |
| Recipient | The owning user's validated email address. Skip and log a safe diagnostic when it is absent or invalid. |
| Configuration | Use Spring's standard `SPRING_MAIL_*` environment variables plus an application-owned sender variable (for example `ALERT_EMAIL_FROM`). All values are configurable and documented in `.env.example` without credentials. |
| Lifecycle | An authenticated owner can acknowledge an alert once. Acknowledgement changes only that alert's status and timestamp; it does not resend mail or affect another user's alert. |
| Idempotency | Record delivery state/attempt metadata sufficient to prevent duplicate sends for the same alert after retries or restarts. |
| Failure handling | Preserve the alert and make delivery failures observable. Retries/backoff must be bounded and configurable; no delivery failure may halt alert detection or unrelated deliveries. |

## Email content

Each email must include the alert type, symbol, trigger time, observed fact/value, configured threshold when relevant, and a link or route hint to view alerts in the application. It must include the MiFID II decision-support disclaimer. The subject must identify the platform and symbol without embedding sensitive account information.

## Out of scope

- Live SMTP/provider acceptance testing in this phase.
- SMS, push notifications, webhooks, digests, notification preference UI, unsubscribe flows, or template branding work.
- Changing G1 detection rules, financial calculations, alert thresholds, or alert priority policy.
- Marking an alert acknowledged automatically after a successful delivery.

## Deferred live-email verification

This phase deliberately does **not** perform live end-to-end email delivery testing. It must use unit/integration tests with a fake or containerized SMTP server, but a real configured SMTP mailbox/provider test is deferred to a later, more advanced development phase before production release. That later phase must confirm credential configuration, provider acceptance, deliverability, rendering, and retry behavior.
