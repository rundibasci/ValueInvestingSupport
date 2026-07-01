# Validation - Phase PI2: Portfolio Intelligence Frontend

## Acceptance Checks

- Portfolio page fetches analytics for the selected portfolio.
- Weighted metrics, sector allocation, holding concentration, moat profile, quality distribution, liquidity, benchmark comparison, and analytics warnings are visible when data exists.
- Holdings table includes liquidity classification and days-to-liquidate information.
- Rebalance proposal displays urgency, transaction cost, total estimated transaction cost, holding period, and position-size warning fields.
- Missing analytics data does not crash the page.
- UI copy remains decision-support oriented and avoids buy/sell instructions.

## Test Strategy

- Run frontend TypeScript validation:
  - `npm run typecheck`
- Run production frontend build:
  - `npm run build`

## Manual QA

- Select an existing portfolio and confirm analytics sections load under the portfolio detail.
- Confirm no text overlaps in the holdings and rebalance tables at desktop widths.
- Confirm unavailable benchmark or liquidity data renders as an explicit status instead of blank output.

## Merge Readiness

- `git status` shows only PI2 spec files and directly related frontend files, excluding pre-existing untracked runtime logs.
- Validation commands pass.
- Obsidian activity note is updated with implementation and validation evidence.
- Phase branch is committed, pushed, changelog is updated, and branch is merged into `main`.

## Known Risks

- Backend analytics may return null or unavailable fields for sparse portfolios; the frontend must tolerate that.
- Portfolio page is already dense, so sections should be compact and scannable.
- Without a running seeded backend, validation is limited to typecheck/build rather than full browser data inspection.

