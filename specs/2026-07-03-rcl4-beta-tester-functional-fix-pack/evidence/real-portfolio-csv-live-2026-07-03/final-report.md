# Real Portfolio CSV Live Beta Report

Generated: 2026-07-03
Mode: non-destructive live probe
CSV: `C:\Users\Marcello\Downloads\Portfolio.csv`
Repo: `C:\Users\Marcello\OneDrive\Documents\workspace\ValueInvestingSupport`

## Verdict

**Blocked** for final CSV portfolio valuation.

The live stack is reachable and existing demo portfolios can be valued, but the real CSV cannot yet be converted into a final app portfolio valuation without a supported mapping/import path. The CSV uses ISIN-like values in `Codice`, while the current search/API universe is ticker-oriented. Only Johnson & Johnson mapped from the CSV by company-name search.

## Evidence Summary

- CSV rows: 15.
- CSV headers: `Prodotto`, `Codice`, `Quantità`, `Ultimo`, `Valore`, `H1`, `Valore in EUR`.
- Blank symbols/codes: 1 cash row.
- Duplicate `Codice`: none.
- Matched CSV securities in local universe: 1 of 14 non-cash holdings, `JOHNSON & JOHNSON` -> `JNJ`.
- Existing app portfolios found: 2 replay portfolios.
- Existing portfolio valuations:
  - `L1 oversized KO concentration`: total value `10486.25`, weighted MoS `-244.56`, 2 concentration warnings.
  - `L1 prudent validation equal weight`: total value `9366.99`, weighted MoS `-178.54`, 2 warnings, including one data-unavailable warning.

## RCL Checks

- Watchlist API: passed, `200`.
- KO review/profile/valuation/dividends/growth/insiders: passed, `200`.
- BRK-B review/profile/valuation/dividends/growth/insiders: passed, `200`.
- BRK.B alias endpoints: blocked on live stack, `404 Symbol not found: BRK.B`.
- Existing replay portfolio still contains `BRK.B` and that holding resolves with missing price/fair value in live stack.
- Screener empty POST `{}`: blocked on live stack, `500 Internal Server Error`.
- RCL2/RCL4 harness evidence exists under dry-run/live-check folders.
- The live backend container was created before the latest RCL pass and appears not rebuilt/restarted from current source.

## Commands Run

- `Import-Csv C:\Users\Marcello\Downloads\Portfolio.csv`
- `Invoke-WebRequest http://localhost:5173/watchlist`
- `Invoke-WebRequest http://localhost:5173/securities/KO/review`
- `POST http://localhost:8080/auth/login` as `investor@realdemo.local`
- `GET http://localhost:8080/api/v1/watchlist`
- `GET http://localhost:8080/api/v1/portfolios`
- `GET http://localhost:8080/api/v1/portfolios/{id}`
- `POST http://localhost:8080/api/v1/screener` with `{}`
- `GET http://localhost:8080/api/v1/conservative-workflow/agent-one-comparison`
- `GET http://localhost:8080/api/v1/securities/{KO|BRK-B|BRK.B}/review`
- `GET http://localhost:8080/api/v1/securities/{KO|BRK-B|BRK.B}/valuation`
- `GET http://localhost:8080/api/v1/securities/search?q=...` for CSV codes/company terms
- `docker ps`
- `docker logs --tail 220 valueinvestingsupport-backend-1`

## Residual Risks

- The current CSV needs an explicit ISIN-to-ticker mapping or importer contract before it can be valued as the user's portfolio.
- Analytics endpoint was not called because it persists a snapshot; this run stayed non-destructive.
- Live Docker stack should be rebuilt/restarted before retesting RCL1 alias and screener fixes.
- `BRK.B` remains visible in Agent 1 comparison while canonical live endpoints accept `BRK-B`, creating a user-facing mismatch if alias normalization is not active.

## Changed Evidence Paths

- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/csv-inspection.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/api-status.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/api-status-v2.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/csv-security-search.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/csv-security-search-v2.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/probe-summary.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/probe-summary-v2.json`
- `specs/2026-07-03-rcl4-beta-tester-functional-fix-pack/evidence/real-portfolio-csv-live-2026-07-03/final-report.md`
