# L2 Plan - Conservative Portfolio Review Pack

1. Review existing conservative workflow surfaces
   - Inspect portfolio, watchlist, review packet, and availability rendering code.
   - Identify the smallest surface that can summarize conservative validation evidence without new backend contracts.
   - Keep Group K/K1/K2/K3 cloud work excluded.

2. Add conservative portfolio review pack UI
   - Add a portfolio review section that summarizes holding weights, sector weights, margin of safety, score availability, valuation availability, data-quality blockers, and watchlist rationale coverage.
   - Flag incomplete validation when any holding is missing current price, sector, score status, or valuation status.
   - Surface conflicts between strong business quality and negative margin of safety without buy/sell language.
   - Keep copy factual and decision-support oriented.

3. Add printable stakeholder summary
   - Provide a journal-style print/export surface suitable for stakeholder review.
   - Include decision-support boundary language.
   - Preserve missing-data and availability state visibility in the printed content.

4. Validation and merge readiness
   - Confirm spec files are present and non-empty.
   - Run frontend typecheck/build for touched UI.
   - Run backend tests only if backend code changes.
   - Update the Obsidian activity note before merge.
   - Commit, push, update changelog, merge to `main`, and push `main` after validation passes.
