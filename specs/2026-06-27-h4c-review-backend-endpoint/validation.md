# H4C - Review Page Backend Review Endpoint Validation

## Functional acceptance

- `GET /api/v1/securities/{symbol}/review` exists and requires authentication.
- Authenticated `INVESTOR`, `ADVISOR`, and `ADMIN` users can request a review packet for seeded shared-universe symbols.
- Unknown or unavailable symbols return the established not-found or unavailable error shape without stack traces or raw provider details.
- The response includes a complete single-stock research packet: profile, financials, ratios, financial health, valuation, dividends, growth, peers, score, source coverage, freshness metadata, data-quality notes, and relevant disclaimer text.
- The response exposes DCF, FCF, Graham number, margin of safety, earnings, debt, dividend sustainability, dividend yield, quick ratio when available, and unavailable-data labels.
- Source coverage is represented by category, including profile, fundamentals, ratios, quote, dividends, valuation, score, peers, and analyst estimates where available.
- Freshness metadata is represented by category, including latest data date/timestamp, provider, fallback status, stale/unavailable state, and provider limitation where supported.
- Missing or unsupported values are labelled explicitly and are not replaced with zero or inferred values.
- Existing valuation guards remain intact, including DCF eligibility behavior for insufficient positive FCF history.
- `SecurityReviewPage` consumes the new review endpoint for its primary research packet instead of composing many individual H4 data endpoints.
- Existing review-page sections, charts, table of contents, scroll progress, custom DCF controls, watchlist action, portfolio-add action, entry points, loading states, partial-data states, and error states remain intact.
- The frontend still uses backend APIs only and never calls FMP or Yahoo Finance directly.
- MiFID II decision-support disclaimer appears wherever fair value, margin of safety, recommendation, score, or valuation-derived context is shown.
- The UI and API never imply trade execution, guarantee returns, or turn recommendation labels into buy/sell advice.

## Automated checks

- Backend unit tests cover:
  - review aggregation success for a fully populated seeded symbol;
  - section DTO mapping for profile/header data;
  - financials, ratios, financial health, dividends, growth, peers, valuation, and score mapping;
  - source coverage and freshness metadata by category;
  - unavailable, stale, unsupported, and provider-limited labels;
  - valuation guard preservation, including DCF-ineligible cases;
  - partial-data behavior where the implementation supports partial responses.
- Backend controller tests cover:
  - unauthenticated requests are rejected;
  - authenticated investor/advisor/admin access succeeds for seeded symbols;
  - unknown symbols return the established not-found response;
  - stale/unavailable data returns the expected status or labelled partial response according to existing backend semantics;
  - the response does not expose secrets, raw credentials, stack traces, or raw provider payloads.
- Frontend tests cover:
  - typed review API client and query hook behavior;
  - `SecurityReviewPage` renders from a single review response;
  - loading, empty, partial-data, unavailable, stale, not-found, expired-session, and network-error states;
  - charts render with provided earnings, debt, ROI/ROIC, and ROE series;
  - unavailable-data labels render for missing quick ratio, dividends, score, analyst estimates, or provider metadata;
  - H4B add-to-portfolio behavior still works;
  - watchlist action and custom DCF controls still use their established APIs;
  - MiFID II disclaimer rendering in valuation/recommendation contexts.
- A deterministic integration or browser test verifies the end-to-end review journey without live FMP/Yahoo calls or secrets:
  1. authenticate as a non-admin investor;
  2. open `/securities/AAPL/review` or another seeded fixture symbol;
  3. verify the page makes the new review request;
  4. verify required review sections and charts render;
  5. verify source/freshness and unavailable-data labels render;
  6. verify add-to-watchlist, custom DCF, and add-to-portfolio actions remain usable where fixtures support them.
- Backend and frontend test suites required by the repo pass.
- Frontend linting, TypeScript type checking, and production build pass.
- `git diff --check` passes.

## Manual review

- Inspect the review endpoint response for a fully populated symbol and a sparse/partial-data symbol.
- Verify desktop and narrow layouts for stable section spacing, readable charts, no text overlap, and no regression to the long-form reading flow.
- Verify source/freshness badges and unavailable-data labels are understandable without implying false precision.
- Verify keyboard navigation, focus order, visible focus states, and screen-reader-readable loading/error/status updates.
- Verify color contrast for source, freshness, stale, unavailable, warning, success, and error states.
- Confirm financial wording remains descriptive and non-directive.
- Confirm review-page portfolio actions still respect user-owned portfolio boundaries while securities remain shared-universe reference data.
- Confirm no provider secrets, JWT refresh tokens, raw credentials, stack traces, internal diagnostics, or sensitive user data appear in source, fixtures, logs, URLs, local storage, or rendered debug output.

## Merge criteria

- The backend exposes `GET /api/v1/securities/{symbol}/review` as the canonical review-packet endpoint.
- The endpoint returns the complete research packet required by H4A, with explicit source coverage, freshness, and unavailable-data metadata.
- `SecurityReviewPage` uses the new endpoint for its primary data source and no longer relies on frontend multi-endpoint composition for the core research packet.
- H4A and H4B user-facing behavior has no regressions.
- Existing auth, shared-universe, portfolio ownership, cache/provider fallback, valuation-guard, and MiFID II decision-support guardrails are preserved.
- Automated checks, deterministic review-page journey, production build, and `git diff --check` pass.
