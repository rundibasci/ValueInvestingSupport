# Validation - Phase RCL4: Beta Tester Functional Fix Pack

## Acceptance Checks

- Beta tester matrix includes investor, advisor/compliance, UI/accessibility, data-quality/API, and real-portfolio CSV tester.
- The CSV tester uses `C:\Users\Marcello\Downloads\Portfolio.csv` by default and never mutates portfolios.
- Cycle evidence includes manifest, persona matrix, CSV inspection, beta gate report, and decision-support boundary note.
- Gate rules require two consecutive clean cycles before K1 and block unresolved high/medium findings.

## Validation Commands

```powershell
powershell -ExecutionPolicy Bypass -File scripts\rcl4-beta-tester-functional-pass.ps1 -SkipLiveApi -CycleId dry-run-rcl4
Get-ChildItem specs\2026-07-03-rcl4-beta-tester-functional-fix-pack\evidence\dry-run-rcl4 -File
```

## Validation Results

- `powershell -ExecutionPolicy Bypass -File scripts\rcl4-beta-tester-functional-pass.ps1 -SkipLiveApi -CycleId dry-run-rcl4` - passed; generated dry-run beta artifacts.
- `Get-ChildItem specs\2026-07-03-rcl4-beta-tester-functional-fix-pack\evidence\dry-run-rcl4 -File` - passed; generated `manifest.json`, `beta-tester-matrix.md`, `portfolio-csv-inspection.json`, `beta-gate-report.md`, and `decision-support-boundary.md`.
- CSV inspection found `C:\Users\Marcello\Downloads\Portfolio.csv`, 15 rows, probable symbol column `Codice`, probable quantity column `Quantità`, probable value column `Valore`, and 1 blank symbol requiring live/manual mapping review.

## Known Risks

- Live beta cycles still require the local app stack and browser checks.
- CSV column names may require manual mapping if the file uses brokerage-specific labels.
- PowerShell `Import-Csv` warned about one unnamed header and assigned default `H1`; the beta report records the header for manual mapping review.
