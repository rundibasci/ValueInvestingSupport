# L2 Requirements - Conservative Portfolio Review Pack

## Scope

Phase L2 adds a conservative portfolio review pack that turns the L1 replay evidence into a stakeholder-readable product surface. The surface must help a prudent-value user review whether a validation portfolio has enough data to assess concentration, valuation, score availability, and watchlist rationale coverage.

The implementation must:

- Summarize holding weights and sector weights.
- Show margin of safety, score availability, valuation availability, and data-quality blockers for portfolio holdings.
- Flag incomplete validation when a holding is missing current price, sector, score status, or valuation status.
- Show conflicts between business quality and negative margin of safety, especially for defensive or high-quality symbols.
- Include watchlist rationale coverage so stakeholders can see which monitored symbols have an explicit reason.
- Provide a printable or exportable journal-style summary.
- Keep all language factual and decision-support oriented.

## Exclusions

- Group K, K1, K2, and K3 cloud distribution work is intentionally excluded by user instruction.
- No GCP, Terraform, deployment, infrastructure, commercial compliance, or production operations changes.
- No buy/sell/order recommendations, personalized advice, or brokerage behavior.
- No new external provider calls solely for the review pack.
- No committed secrets, tokens, provider payload archives, or live-market-data snapshots.

## Decisions

| Decision | Rationale |
|---|---|
| Implement this as a frontend review/report surface | The roadmap asks for a surface or report section and existing backend APIs already expose portfolio, watchlist, scoring, valuation, and availability information. |
| Use derived frontend evidence rows | This avoids introducing a brittle backend aggregation contract before RD2 validation proves the exact shape needed. |
| Add print support through browser printing | It satisfies the printable/exportable requirement without adding PDF generation dependencies. |
| Treat missing values as validation findings | The mission requires explainable missing data instead of blank metrics. |

## Assumptions

- Existing frontend portfolio/watchlist/review APIs contain enough fields to derive a conservative evidence summary.
- If some live API fields are absent, the UI can mark those checks as incomplete instead of inventing data.
- Browser print output is acceptable as the exportable journal-style summary for this phase.
- The earliest unstarted non-K phase after L1 is L2; this phase was selected because K phases are excluded and L1 already exists.

## Dependencies

- Mission principles for decision-support boundaries, data before opinion, conservative defaults, explainable missing data, and portfolio exposure visibility.
- Frontend React 18, TypeScript, Vite, TailwindCSS, React Router, and TanStack Query.
- Existing portfolio, watchlist, review packet, score, valuation, and availability UI/domain models.
