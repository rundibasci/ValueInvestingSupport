# Google OAuth Operations Runbook

## Cloud Console Setup

1. Create or select the Google Cloud project used for the environment.
2. Configure the OAuth consent screen for an internal or limited stakeholder audience.
3. Create an OAuth 2.0 Web application client.
4. Register exact redirect URIs for each environment:
   - Local frontend/API: `http://localhost:8080/login/oauth2/code/google`
   - Local alternate host: `http://127.0.0.1:8080/login/oauth2/code/google`
   - Stakeholder HTTPS environment: `https://<api-host>/login/oauth2/code/google`
5. Register only the scopes required by the platform: `openid`, `email`, and `profile`.

## Runtime Configuration

Set these values outside source control:

```properties
GOOGLE_CLIENT_ID=<oauth-client-id>
GOOGLE_CLIENT_SECRET=<oauth-client-secret>
GOOGLE_REDIRECT_URI={baseUrl}/login/oauth2/code/google
GOOGLE_FRONTEND_CALLBACK=http://localhost:5173/auth/oauth2/callback
```

Local development should keep these in `.env`. Deployed environments should inject them from the environment's secret manager. Do not commit real client IDs, client secrets, authorization codes, ID tokens, access tokens, or refresh tokens.

## Validation Checklist

- `GET /oauth2/authorization/google` starts the Google authorization-code flow.
- Google redirects back to `/login/oauth2/code/google`.
- The backend rejects unverified Google emails.
- The backend creates or links only a platform user and then issues normal platform JWTs.
- The frontend receives only the short-lived handoff code in the callback URL.
- The handoff exchange at `/auth/oauth2/token` is single-use.
- `/api/**` routes still require a platform JWT.

## Logging And Metrics

The application emits sanitized OAuth security events with provider, event, outcome, and reason tags. The event stream must never include:

- Google ID tokens
- Authorization codes
- Google client secrets
- Platform access or refresh tokens
- Raw profile claim payloads

Expected event categories include callback success/rejection, identity creation/linking/reuse, and handoff exchange success/rejection.

## Secret Rotation

1. Create a replacement Google OAuth client secret in Cloud Console.
2. Update the environment secret value.
3. Redeploy or restart the application instance that consumes the secret.
4. Run the validation checklist.
5. Delete the old secret from Cloud Console after validation passes.
6. Record the rotation date and operator in the environment operations log.

## Compromised Client Secret Response

1. Immediately disable or delete the compromised secret in Cloud Console.
2. Rotate to a new client secret and redeploy the affected environment.
3. Revoke active platform refresh tokens if there is evidence of account compromise.
4. Review OAuth security events for unusual callback failures, account creation spikes, or repeated handoff exchange failures.
5. Confirm no logs contain sensitive OAuth material.
6. Document incident timeline, impact assessment, user notification decision, and follow-up actions.
