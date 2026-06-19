-- Phase B3: Data Ingestion Jobs (H2-compatible)
-- Differences: RANDOM_UUID() instead of gen_random_uuid(), no CHECK constraint (H2 syntax differs)

CREATE TABLE job_run_log (
    id                 UUID         NOT NULL DEFAULT RANDOM_UUID(),
    job_name           VARCHAR(100) NOT NULL,
    started_at         TIMESTAMP    NOT NULL,
    completed_at       TIMESTAMP,
    status             VARCHAR(20)  NOT NULL,
    records_processed  INT,
    error_message      VARCHAR(32767),
    PRIMARY KEY (id)
);

CREATE INDEX idx_job_run_log_name_started ON job_run_log(job_name, started_at DESC);

ALTER TABLE valuation_result ADD COLUMN source VARCHAR(30);

CREATE INDEX idx_valuation_source ON valuation_result(security_id, source);
