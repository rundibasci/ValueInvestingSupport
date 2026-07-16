CREATE TABLE portfolio_analysis_run (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    portfolio_id UUID NOT NULL REFERENCES portfolio(id) ON DELETE CASCADE,
    import_id UUID REFERENCES portfolio_import(id) ON DELETE SET NULL,
    retry_of_id UUID REFERENCES portfolio_analysis_run(id) ON DELETE SET NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    analysis_version VARCHAR(40) NOT NULL,
    symbols TEXT NOT NULL,
    input_snapshot TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    phase VARCHAR(30) NOT NULL,
    total_count INTEGER NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    succeeded_count INTEGER NOT NULL DEFAULT 0,
    partial_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    current_symbol VARCHAR(20),
    terminal_reason VARCHAR(500),
    analytics_snapshot_id UUID REFERENCES portfolio_analytics_snapshot(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT portfolio_analysis_counts_valid CHECK (
        total_count >= 0 AND processed_count >= 0 AND succeeded_count >= 0
        AND partial_count >= 0 AND failed_count >= 0
        AND processed_count = succeeded_count + partial_count + failed_count
        AND processed_count <= total_count
    )
);

CREATE INDEX idx_portfolio_analysis_owner_created ON portfolio_analysis_run(user_id, portfolio_id, created_at DESC);
CREATE INDEX idx_portfolio_analysis_active ON portfolio_analysis_run(user_id, portfolio_id, request_fingerprint, status);

CREATE TABLE portfolio_analysis_outcome (
    id UUID PRIMARY KEY,
    analysis_run_id UUID NOT NULL REFERENCES portfolio_analysis_run(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(60),
    refreshed_at DATE,
    source_last_price NUMERIC(20,6),
    source_base_value NUMERIC(20,4),
    refreshed_price NUMERIC(20,6),
    price_variance_percent NUMERIC(12,4),
    reason_code VARCHAR(80),
    reason VARCHAR(500),
    fallback_reason VARCHAR(1000),
    error_message VARCHAR(500),
    review_path VARCHAR(200),
    calculation_version VARCHAR(40) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uq_portfolio_analysis_outcome_position UNIQUE(analysis_run_id, position),
    CONSTRAINT uq_portfolio_analysis_outcome_symbol UNIQUE(analysis_run_id, symbol)
);

CREATE INDEX idx_portfolio_analysis_outcome_run_position
    ON portfolio_analysis_outcome(analysis_run_id, position);
