CREATE INDEX IF NOT EXISTS idx_security_company_name
    ON security(company_name);

CREATE INDEX IF NOT EXISTS idx_fundamental_snapshot_security_period_date
    ON fundamental_snapshot(security_id, period, report_date DESC);

CREATE INDEX IF NOT EXISTS idx_ratio_snapshot_security_date
    ON ratio_snapshot(security_id, report_date DESC);

CREATE INDEX IF NOT EXISTS idx_dividend_record_security_date
    ON dividend_record(security_id, ex_dividend_date DESC);

CREATE INDEX IF NOT EXISTS idx_insider_trade_security_date
    ON insider_trade(security_id, trade_date DESC);
