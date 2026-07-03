# Requirements - Phase RCL3: Security Detail Historical Chart And Data Verification Pass

## Scope

Phase RCL3 addresses the security-detail and review-page trust issues reported during investor replay, with KO as the primary smoke target. The phase improves historical-chart readability, avoids misleading repeated-value charts when history is unavailable, adds history-window selection where the UI has real series data, and ensures valuation actions surface visible feedback.

## Required Outcomes

- Security-detail/review charts show readable quote/history axes and labels.
- Users can select a sensible history window where historical depth exists.
- Ratio, return, valuation, P/E, and capital-structure charts do not graph synthetic flat lines when no real history exists; the UI must show the current value as text with an unavailable-history note.
- Financial-health resilience indicators must not imply a trend when only current/sparse values exist.
- `Run FCF` must show visible feedback for success, validation failure, guardrail-blocked, provider-limited, and unexpected failure outcomes.
- Dividends, growth, and insider panels must expose source/freshness/unavailable states consistently with backend responses.

## Decisions

- RCL3 is allowed to make conservative UI changes that reduce chart surface when history is sparse.
- The default history window is `10y` when enough data exists, otherwise `max`.
- Missing historical data should be explicit and text-first rather than represented by repeated plotted points.

## Out Of Scope

- Full beta tester rerun and CSV portfolio validation; those belong to RCL4.
- Cloud deployment readiness; Group K begins only after RCL4 gates are done.
