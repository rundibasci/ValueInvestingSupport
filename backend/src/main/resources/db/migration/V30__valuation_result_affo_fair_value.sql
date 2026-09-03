-- RM5 (specs/2026-09-03-rm5-reit-composite-fair-value/): for a REIT-classified security,
-- composite_fair_value/margin_of_safety come from an AFFO-based multiple instead of the
-- GAAP-anchored DCF/Graham/DDM blend used for every other sector. This column holds that
-- AFFO-based figure separately so the security-detail page can distinguish "not a REIT" from
-- "REIT but insufficient AFFO history" from "REIT with a computed AFFO fair value" — both cases
-- are null on composite_fair_value alone. Always null for a non-REIT security.
ALTER TABLE valuation_result ADD COLUMN affo_fair_value NUMERIC(15, 4);
