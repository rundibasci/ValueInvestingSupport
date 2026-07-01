CREATE TABLE portfolio_analytics_snapshot (
    id UUID PRIMARY KEY,
    portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    captured_at TIMESTAMP NOT NULL,
    total_market_value NUMERIC(20,4),
    benchmark_symbol VARCHAR(20) NOT NULL,
    warning_count INTEGER NOT NULL,
    payload CLOB NOT NULL
);

CREATE INDEX idx_portfolio_analytics_snapshot_portfolio
    ON portfolio_analytics_snapshot(portfolio_id, captured_at DESC);
