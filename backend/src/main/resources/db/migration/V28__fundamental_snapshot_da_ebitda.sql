-- RM1 (specs/sector-aware-valuation-metrics.md): FFO/Debt-EBITDA inputs, confirmed present on
-- FMP Premium's /income-statement (verified live against O, PLD, SPG). Null for Yahoo-sourced
-- snapshots (Design Principle 5 fallback has no equivalent field).
ALTER TABLE fundamental_snapshot ADD COLUMN depreciation_and_amortization NUMERIC(20, 2);
ALTER TABLE fundamental_snapshot ADD COLUMN ebitda NUMERIC(20, 2);
