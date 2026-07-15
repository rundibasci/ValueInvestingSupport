CREATE TABLE seed_run (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    scope VARCHAR(40) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    symbols TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    total_count INTEGER NOT NULL,
    processed_count INTEGER NOT NULL DEFAULT 0,
    succeeded_count INTEGER NOT NULL DEFAULT 0,
    partial_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    current_symbol VARCHAR(20),
    terminal_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT seed_run_counts_valid CHECK (
        total_count >= 0 AND processed_count >= 0 AND succeeded_count >= 0
        AND partial_count >= 0 AND failed_count >= 0
        AND processed_count = succeeded_count + partial_count + failed_count
        AND processed_count <= total_count
    )
);

CREATE INDEX idx_seed_run_user_created ON seed_run(user_id, created_at DESC);
CREATE INDEX idx_seed_run_active_fingerprint ON seed_run(user_id, scope, request_fingerprint, status);

CREATE TABLE seed_run_outcome (
    id UUID PRIMARY KEY,
    seed_run_id UUID NOT NULL REFERENCES seed_run(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    source VARCHAR(60),
    reason_code VARCHAR(80),
    reason VARCHAR(500),
    fallback_reason VARCHAR(1000),
    error_message VARCHAR(500),
    completed_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_seed_run_outcome_position UNIQUE(seed_run_id, position),
    CONSTRAINT uq_seed_run_outcome_symbol UNIQUE(seed_run_id, symbol)
);

CREATE INDEX idx_seed_run_outcome_run_position ON seed_run_outcome(seed_run_id, position);
