# J3 Google Sign-In Security, Integration & Operational Validation Plan

1. Spec and branch setup
   - Create `phase/j3-google-sign-in-validation`.
   - Add requirements, plan, and validation notes for the J3 scope.
   - Confirm existing untracked runtime logs are unrelated and leave them untouched.

2. Backend security telemetry
   - Add a small OAuth security event service that logs structured event names/reasons without sensitive OAuth material.
   - Record counters for success, rejection, account creation/linking/reuse, and handoff exchange outcomes.
   - Wire telemetry into account resolution, callback success/rejection, and handoff token exchange.

3. Backend validation tests
   - Extend identity resolution tests for conflicting provider subject and email normalization behavior if absent.
   - Add handler-level tests for verified Google callback success, unverified email rejection, refresh-cookie creation, redirect handoff code, and Redis fallback.
   - Keep `/api/**` authorization regression coverage in the OAuth token exchange tests.

4. Operational documentation
   - Add a Google OAuth operational runbook covering Cloud Console setup, redirect URIs, local development, secret rotation, and compromised-client-secret response.
   - State explicitly what must never be logged or committed.

5. Frontend and full validation
   - Run focused backend OAuth tests first.
   - Run the backend Maven test suite.
   - Run frontend `npm run build`.
   - Record validation results in the vault activity note before commit and merge.
