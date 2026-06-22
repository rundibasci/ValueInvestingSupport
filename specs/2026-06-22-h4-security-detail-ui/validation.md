# H4 — Security Detail UI Validation

H4 is complete and mergeable only when all checks below pass.

## Automated checks

- Frontend TypeScript strict-mode check, tests, and production build succeed.
- API-client and query-hook tests cover every Security Detail endpoint, symbol encoding, nullable fields, stale-data responses, authenticated requests, retry, and session expiry.
- Component/integration tests cover the persistent summary header, all eight tabs, lazy tab loading, charts/tables, loading/refetch/empty states, unavailable values, and retention of already-loaded content.
- Tests cover the custom DCF form's validation and displayed server response, Add to Watchlist success/duplicate/failure states, and the MiFID II disclaimer in valuation content.
- Error-popup tests verify safe returned details, accessible announcement/focus, retry and dismiss actions, and that tokens, stack traces, and internal diagnostics are never rendered.
- Relevant existing backend Security Detail, valuation, and watchlist tests remain green.

## Browser acceptance checks

| Scenario | Expected result |
| --- | --- |
| Open `/securities/AAPL` while authenticated | The live profile API populates a clear summary header, overview content, and visible research navigation. |
| Open the route while signed out | H2 protection sends the user to login and returns them to the requested security after authentication. |
| Open every tab | Its live API data loads once, is formatted with units/dates, and uses a clear loading, empty, stale, or unavailable state where appropriate. |
| Inspect Financials, Ratios, and Financial Health | Charts show their named measures and dates; unavailable values are not zeroed; health content includes definitions/data availability/context and avoids universal ratings. |
| Inspect Valuation | Price, fair value, MoS/range, assumptions, and the MiFID II decision-support disclaimer are visible; a valid custom DCF submission shows the server response. |
| Inspect Dividends, Growth, and Insider | Dividend history/streak/CAGRs and available payout/coverage, 3/5/10-year growth, and recent trades are accurately displayed. |
| Add the security to a watchlist | The existing authenticated API is called and success, duplicate, or failure feedback is clear without navigating away unexpectedly. |
| A tab API returns an error | A safe on-screen popup exposes the useful server detail with retry/dismiss controls; working header and other tabs remain usable. |
| Profile or valuation is stale | The page explains the supplied stale-data condition and date rather than fabricating a current value. |
| Test narrow viewport and keyboard-only navigation | Tabs, forms, charts/tables, dialog, watchlist action, and retry/dismiss controls remain usable with visible focus and no keyboard trap. |

## Merge gates

- All eight H4 research tabs and the persistent overview/header consume real authenticated application APIs; no mock financial data or direct provider integration is introduced.
- The interface makes the value-investing evidence legible, preserves the decision-support boundary, and includes a MiFID II disclaimer for valuation content.
- Financial-health presentation is trend/context based and does not imply universal leverage thresholds or personalised investment advice.
- Errors appear as safe, accessible on-screen popups with actionable details and never reveal credentials, JWTs, stack traces, or sensitive internal diagnostics.
- Existing application route protection and backend authorization continue to govern access.
- The working tree contains only intentional H4 specification changes plus the pre-existing user-owned log files; active branch is `feature/h4-security-detail-ui`.
