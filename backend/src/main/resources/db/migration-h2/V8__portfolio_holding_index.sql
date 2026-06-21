CREATE INDEX IF NOT EXISTS idx_holding_portfolio ON holding(portfolio_id);
CREATE INDEX IF NOT EXISTS idx_holding_portfolio_symbol ON holding(portfolio_id, symbol);
