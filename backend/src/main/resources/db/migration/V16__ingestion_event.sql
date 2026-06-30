-- Phase JC1: per-symbol ingestion event log

CREATE TABLE ingestion_event (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    job_run_id    UUID,
    job_name      VARCHAR(100) NOT NULL,
    symbol        VARCHAR(20),
    data_type     VARCHAR(40)  NOT NULL,
    status        VARCHAR(20)  NOT NULL CHECK (status IN ('SUCCESS', 'SKIPPED', 'FAILED')),
    source        VARCHAR(30),
    error_detail  TEXT,
    occurred_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_ingestion_event_run ON ingestion_event(job_run_id, occurred_at DESC);
CREATE INDEX idx_ingestion_event_job ON ingestion_event(job_name, occurred_at DESC);
CREATE INDEX idx_ingestion_event_symbol ON ingestion_event(symbol, occurred_at DESC);
CREATE INDEX idx_ingestion_event_status ON ingestion_event(status, occurred_at DESC);
