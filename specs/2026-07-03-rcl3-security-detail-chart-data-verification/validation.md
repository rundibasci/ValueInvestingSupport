# Validation - Phase RCL3: Security Detail Historical Chart And Data Verification Pass

## Acceptance Checks

- KO security-detail/review history sections remain readable at desktop and narrow widths.
- History-window controls appear only where they make sense for real historical series.
- Sparse or repeated historical data is rendered as text with an explicit unavailable-history note instead of a misleading chart.
- FCF run action produces visible feedback for success and failure/guardrail states.
- Dividends, growth, and insider panels distinguish unavailable/provider-limited data from true zero history/activity.

## Validation Commands

```powershell
cd frontend; npm run typecheck
cd frontend; npm run build
```

## Validation Results

- `cd frontend; npm run typecheck` - passed.
- `cd frontend; npm run build` - passed; Vite reported only the existing large chunk warning.
- Static validation confirms security review/detail chart components now avoid charting sparse or repeated-only history and expose `3y`, `5y`, `10y`, and `max` windows when enough rows exist.

## Manual QA

- Open `http://localhost:5173/securities/KO`.
- Open `http://localhost:5173/securities/KO/review`.
- Verify quote/history labels, ratio/return/valuation/P/E/capital-structure fallback behavior, FCF feedback, and dividends/growth/insider availability copy.

## Known Risks

- Without a running real-demo stack, browser QA is limited to static/type/build validation.
- Historical data availability varies by provider and seeded universe.
- Live KO route checks must be captured in the next investor/monitor cycle when the real-demo stack is available.
