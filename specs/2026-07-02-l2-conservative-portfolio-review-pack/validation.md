# L2 Validation - Conservative Portfolio Review Pack

## Acceptance Checks

- `plan.md`, `requirements.md`, and `validation.md` exist and are non-empty.
- The frontend exposes a conservative portfolio review pack/report section.
- The review pack summarizes holding weights, sector weights, margin of safety, score availability, valuation availability, data-quality blockers, and watchlist rationale coverage.
- Holdings with missing current price, sector, score status, or valuation status are flagged as incomplete validation.
- Business-quality/negative-margin-of-safety conflicts are visible without buy/sell language.
- The report includes a print/export action and decision-support boundary copy.
- No Group K/K1/K2/K3 files or infrastructure are changed.

## Validation Commands

Run from the repository root:

```powershell
Test-Path specs/2026-07-02-l2-conservative-portfolio-review-pack/plan.md
Test-Path specs/2026-07-02-l2-conservative-portfolio-review-pack/requirements.md
Test-Path specs/2026-07-02-l2-conservative-portfolio-review-pack/validation.md
cd frontend; npm run typecheck
cd frontend; npm run build
```

## Manual QA

With the frontend running against a local backend:

```powershell
cd frontend
npm run dev
```

Open the portfolio/review workflow and confirm:

- The conservative review pack renders for a validation portfolio.
- Missing price, sector, score, or valuation states are shown as incomplete validation items.
- Watchlist rationale coverage is visible.
- The print/export action opens browser print with the journal summary content.
- Copy remains factual and does not describe a portfolio as investable or issue buy/sell instructions.

## Merge Readiness

- Spec files are non-empty.
- Frontend typecheck and build pass.
- Backend tests are run only if backend code changes.
- Git diff is limited to L2 spec files, frontend review-pack implementation, changelog, and vault activity documentation.

## Known Risks

- Existing backend responses may not include every field needed for complete evidence. The UI must surface incomplete states instead of hiding them.
- Browser print rendering varies by client, but the journal summary must remain readable and structured.
- RD2 may identify additional conservative workflow evidence fields; those should remain follow-up work rather than expanding L2 beyond its scope.
