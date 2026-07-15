CREATE TABLE market_data_fallback_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_run_id UUID,
    job_name VARCHAR(100),
    symbol VARCHAR(20) NOT NULL,
    operation VARCHAR(40) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    trigger_reason VARCHAR(50) NOT NULL,
    primary_provider VARCHAR(30) NOT NULL,
    fallback_provider VARCHAR(30) NOT NULL,
    primary_status VARCHAR(80),
    outcome VARCHAR(20) NOT NULL,
    missing_fields VARCHAR(500),
    accepted_fields VARCHAR(500),
    error_detail VARCHAR(1000),
    duration_ms BIGINT NOT NULL,
    occurred_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_fallback_event_occurred_at ON market_data_fallback_event (occurred_at DESC);
CREATE INDEX idx_fallback_event_symbol ON market_data_fallback_event (symbol, occurred_at DESC);
CREATE INDEX idx_fallback_event_job_run ON market_data_fallback_event (job_run_id, occurred_at DESC);
CREATE INDEX idx_fallback_event_type_outcome ON market_data_fallback_event (event_type, outcome, occurred_at DESC);
CREATE INDEX idx_fallback_event_trigger ON market_data_fallback_event (trigger_reason, occurred_at DESC);

