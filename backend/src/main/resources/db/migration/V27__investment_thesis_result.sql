CREATE TABLE investment_thesis_result (
    id UUID PRIMARY KEY,
    security_id UUID NOT NULL REFERENCES security(id),
    request_id UUID NOT NULL UNIQUE,
    requested_by_user_id UUID NOT NULL REFERENCES app_user(id),
    model_id VARCHAR(128) NOT NULL,
    model_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64) NOT NULL,
    input_snapshot TEXT NOT NULL,
    output_json TEXT,
    classification VARCHAR(32),
    confidence NUMERIC(3,2),
    human_review_required BOOLEAN,
    data_warnings_present BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    raw_output_available BOOLEAN NOT NULL DEFAULT FALSE,
    latency_ms INTEGER,
    generated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT investment_thesis_status_valid CHECK (
        status IN ('GENERATING', 'READY', 'FAILED', 'HUMAN_REVIEW_PENDING')
    )
);

-- Latest-thesis-per-security lookup (GET /api/v1/securities/{symbol}/thesis).
CREATE INDEX idx_thesis_security_generated ON investment_thesis_result (security_id, generated_at DESC);

-- Review queue (GET /api/v1/admin/thesis/review-queue): status=HUMAN_REVIEW_PENDING or
-- data_warnings_present=true, per TRAIN-12.5's audit-retention scope. data_warnings_present
-- is a plain boolean column (not a JSONB predicate) precisely so this can be a normal partial
-- index instead of depending on Postgres JSONB-emptiness semantics.
CREATE INDEX idx_thesis_review_queue ON investment_thesis_result (generated_at DESC)
    WHERE status = 'HUMAN_REVIEW_PENDING' OR data_warnings_present = TRUE;
