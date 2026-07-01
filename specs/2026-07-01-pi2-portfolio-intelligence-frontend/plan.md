# Plan - Phase PI2: Portfolio Intelligence Frontend

1. Inspect existing portfolio UI and PI1 API contracts.
   - Read `frontend/src/pages/PortfolioPage.tsx` and `frontend/src/api/portfolio.ts`.
   - Read PI1 portfolio analytics and rebalance DTOs.
   - Keep UI changes on the existing portfolio route instead of adding a new navigation surface.

2. Add frontend API types and analytics fetch.
   - Add TypeScript types for weighted metrics, sector weights, holding concentration, moat profile, quality distribution, liquidity, benchmark comparison, and analytics warnings.
   - Add `portfolioApi.analytics(id)` for `GET /api/v1/portfolios/{id}/analytics`.
   - Extend rebalance response types with PI1 fields: urgency, estimated transaction cost, total transaction cost, holding period, and position-size warning.

3. Build portfolio analytics dashboard sections.
   - Fetch analytics for the selected portfolio.
   - Add weighted metric summary tiles for MoS, P/E, dividend yield, value score, and F-Score.
   - Add sector allocation chart with concentration flags.
   - Add holding concentration bars with immaterial and concentrated states.
   - Add moat profile and quality distribution panels.
   - Surface analytics warnings without implying trade advice.

4. Add liquidity diagnostics.
   - Add a liquidity column to the holdings table.
   - Show classification, days to liquidate, and availability status.
   - Highlight illiquid holdings prominently.

5. Add benchmark and smart rebalance UI.
   - Add a benchmark comparison panel using the PI1 default benchmark response.
   - Enhance the rebalance table with urgency, transaction cost, holding period, and position-size warnings.
   - Show total estimated transaction cost in the rebalance summary.

6. Validate frontend.
   - Run `npm run typecheck`.
   - Run `npm run build`.
   - Review `git diff --stat` and ensure changes are scoped to PI2 spec and frontend portfolio files.

