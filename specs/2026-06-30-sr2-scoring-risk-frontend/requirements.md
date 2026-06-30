# SR2 Scoring & Risk Frontend Requirements

## Scope

Implement the frontend surfaces for Phase SR2: make the scoring and risk intelligence added in SR1 visible on the React application.

The phase covers:

1. Review-page score display with MoS gate status, raw score, capped score, and applied sector weight profile.
2. Review-page cards for Piotroski F-Score, Altman Z-Score, cyclicality, and earnings quality.
3. Screener table columns for Piotroski score and Altman zone.
4. Cross-symbol comparison support for Piotroski, Altman, cyclicality, and earnings quality where comparison data is already available.
5. Clear missing-data states so blank metrics are never shown without explanation.

## Exclusions

1. No new backend persistence, migrations, or SR1 calculation changes.
2. No new API endpoints unless the existing frontend client cannot consume already exposed fields.
3. No investment advice copy, buy/sell language, or personalized recommendation language.
4. No redesign of unrelated dashboard, auth, portfolio, or watchlist workflows.

## Decisions

1. Use existing React 18, TypeScript, TailwindCSS, Recharts, and local component patterns.
2. Prefer compact, data-dense cards over marketing-style panels.
3. Treat missing SR1 data as an availability state and display a factual message instead of hiding the section.
4. Display score gate copy as decision-support context: "Score capped at 40 because price is above composite fair value."
5. Keep profile switching comparison local to the UI unless a backend override endpoint already exists.

## Assumptions

1. SR1 backend fields are exposed either directly in the review response or through existing score/risk endpoints consumed by the review page.
2. The review page is the primary surface for detailed cards; screener and comparison views receive concise columns/cells.
3. The current frontend already has enough route and API structure to add typed fields without changing authentication flow.
4. If backend data is partially absent, this phase adds resilient rendering and tests rather than expanding backend calculations.

## Dependencies

1. Phase SR1 backend is merged into `main`.
2. Frontend build and test scripts in `frontend/package.json`.
3. Existing review, screener, and comparison UI code.

## Roadmap Context

SR2 completes the user-facing half of Group SR. It makes the MoS gate, sector-adaptive profile, Piotroski, Altman, cyclicality, and earnings-quality outputs understandable in the main research workflow before Group MA adds moat and business-quality analysis.
