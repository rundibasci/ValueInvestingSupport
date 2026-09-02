-- RM2 (specs/sector-aware-valuation-metrics.md §4.2): AFFO's recurring-capex input, same posture
-- as RM1's depreciation_and_amortization/ebitda columns above. Null for Yahoo-sourced snapshots.
ALTER TABLE fundamental_snapshot ADD COLUMN capital_expenditure NUMERIC(20, 2);

-- RM2: EBITDA interest coverage input, confirmed live against FMP Premium's /income-statement
-- for O, PLD, and SPG during RM2 (re-verified, not assumed from RM1's earlier field list).
ALTER TABLE fundamental_snapshot ADD COLUMN interest_expense NUMERIC(20, 2);

-- RM2 (specs/sector-aware-valuation-metrics.md §2, §7): VIS-computed REIT sector metrics.
-- All columns nullable — populated only for REIT/real-estate-classified securities
-- (SectorClassifier.isReit), by SectorMetricService; every other row (non-REIT sectors,
-- Yahoo-fallback rows) leaves them null, per Design Principle 5 and Design Principle 12.
ALTER TABLE ratio_snapshot ADD COLUMN ffo_per_share NUMERIC(12, 4);
ALTER TABLE ratio_snapshot ADD COLUMN affo_per_share NUMERIC(12, 4);
ALTER TABLE ratio_snapshot ADD COLUMN price_to_ffo NUMERIC(10, 4);
ALTER TABLE ratio_snapshot ADD COLUMN price_to_affo NUMERIC(10, 4);
ALTER TABLE ratio_snapshot ADD COLUMN net_debt_to_ebitda NUMERIC(10, 4);
ALTER TABLE ratio_snapshot ADD COLUMN interest_coverage_ebitda NUMERIC(10, 4);
ALTER TABLE ratio_snapshot ADD COLUMN affo_payout_ratio NUMERIC(10, 4);
