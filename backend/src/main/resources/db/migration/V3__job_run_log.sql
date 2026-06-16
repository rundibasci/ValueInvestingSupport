-- Phase B3: Data Ingestion Jobs

CREATE TABLE job_run_log (
    id                 UUID         NOT NULL DEFAULT gen_random_uuid(),
    job_name           VARCHAR(100) NOT NULL,
    started_at         TIMESTAMP    NOT NULL,
    completed_at       TIMESTAMP,
    status             VARCHAR(20)  NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
    records_processed  INT,
    error_message      TEXT,
    PRIMARY KEY (id)
);

CREATE INDEX idx_job_run_log_name_started ON job_run_log(job_name, started_at DESC);

ALTER TABLE valuation_result ADD COLUMN source VARCHAR(30);

CREATE INDEX idx_valuation_source ON valuation_result(security_id, source);
