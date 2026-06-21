CREATE INDEX IF NOT EXISTS idx_value_score_security_date
    ON value_score(security_id, score_date DESC);

CREATE INDEX IF NOT EXISTS idx_security_sector ON security(sector);
CREATE INDEX IF NOT EXISTS idx_security_exchange ON security(exchange);
CREATE INDEX IF NOT EXISTS idx_ratio_snapshot_security_date
    ON ratio_snapshot(security_id, report_date DESC);
CREATE INDEX IF NOT EXISTS idx_valuation_result_security_date
    ON valuation_result(security_id, valuation_date DESC);
